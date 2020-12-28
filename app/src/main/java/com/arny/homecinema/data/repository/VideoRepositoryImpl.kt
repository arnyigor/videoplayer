package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.network.sources.IVideoSourceFactory
import com.arny.homecinema.data.repository.sources.cache.VideoCache
import com.arny.homecinema.data.repository.sources.store.StoreProvider
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter,
    private val hostStore: IHostStore,
    private val sourceFactory: IVideoSourceFactory,
    private val storeProvider: StoreProvider,
    private val videoCache: VideoCache,
) : VideoRepository {

    @Volatile
    private var currentMovie: Movie? = null

    override fun searchMovie(search: String): Flow<MutableList<Movie>> {
        return flow {
            emit(
                videoApiService.searchVideo(
                    getSource().searchUrl,
                    getSource().getSearchFields(search),
                    getSource().searchHeaders,
                )
            )
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                mutableListOf<Movie>().apply {
                    for (link in getSearchResultLinks(doc)) {
                        add(getVideoFromLink(link))
                    }
                }
            }
    }

    private fun getSearchResultLinks(doc: Document) = getSource().getSearchResultLinks(doc)

    private fun getSource() = sourceFactory.createSource(
        hostStore,
        videoApiService,
        responseBodyConverter
    )

    override fun getAllVideos(): Flow<DataResult<MainPageContent>> {
        return flow {
            getHostsData()
            emit(videoApiService.requestMainPage(hostStore.baseUrl, hostStore.mainPageHeaders))
        }
            .map(::getMainPageContent)
            .flowOn(Dispatchers.IO)
    }

    override fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>> {
        return flow {
            val url = hostStore.baseUrl + type?.substringAfter("/")
            emit(videoApiService.requestTyped(url))
        }
            .map(::getMainPageContent)
            .flowOn(Dispatchers.IO)
    }

    private fun getMainPageContent(body: ResponseBody): DataResult<MainPageContent> {
        val doc = responseBodyConverter.convert(body)
        requireNotNull(doc)
        return MainPageContent(getMainVideos(doc), getMenuLinks(doc)).toResult()
    }

    private fun getMenuLinks(doc: Document): MutableList<VideoSearchLink> {
        val menuItems = getSource().getMenuItems(doc)
        return mutableListOf<VideoSearchLink>().apply {
            for (link in menuItems) {
                add(getVideoSearchFromLink(link))
            }
        }
    }

    private fun getMainVideos(doc: Document): MutableList<Movie> {
        return mutableListOf<Movie>().apply {
            for (link in getMainPageLinks(doc)) {
                add(getVideoFromLink(link))
            }
        }
    }

    private fun getVideoFromLink(link: Element): Movie = getSource().getVideoFromLink(link)

    private fun getMainPageLinks(doc: Document) = getSource().getMainPageLinks(doc)

    private fun getVideoSearchFromLink(link: Element) =
        VideoSearchLink(link.text(), link.attr("href"))

    override fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>> {
        return flow {
            if (movie != null) {
                videoCache.addToCache(movie)
                if (storeProvider.canSaveToStore()) {
                    storeProvider.saveToStore(movie)
                }
                emit(true.toResult())
            } else {
                emit(false.toResult())
            }
        }.flowOn(Dispatchers.IO)
    }

    @FlowPreview
    override fun searchCached(searchText: String): Flow<List<Movie>> {
        return flow {
            var movies = emptyList<Movie>()
            if (searchText.length > 1) {
                movies = videoCache.searchFromCache(searchText)
                if (movies.isEmpty() && isSaveToStore()) {
                    movies = try {
                        storeProvider.searchMovies(searchText)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            emit(movies)
        }
            .debounce(350)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    }

    private fun isSaveToStore(): Boolean = storeProvider.canSaveToStore()

    override fun loadMovie(movie: Movie): Flow<DataResult<Movie>> {
        return flow {
            val movieInStore = videoCache.getFromCache(movie.title)
            val resultMovie = if (movieInStore != null) {
                currentMovie = movieInStore
                currentMovie!!
            } else {
                val fromPrefs = getFromStore(movie)
                if (fromPrefs != null) {
                    videoCache.addToCache(fromPrefs)
                    currentMovie = fromPrefs
                    currentMovie!!
                } else {
                    val value = getFullMovie(movie)
                    videoCache.addToCache(value)
                    if (storeProvider.canSaveToStore()) {
                        storeProvider.saveToStore(value)
                    }
                    currentMovie = value
                    currentMovie!!
                }
            }
            emit(resultMovie.toResult())
        }.flowOn(Dispatchers.IO)
    }

    private fun getFromStore(movie: Movie): Movie? {
        return storeProvider.getFromStore(movie.title)
    }

    override fun setHost(source: String, resetHost: Boolean) {
        hostStore.host = source
        if (resetHost) {
            hostStore.saveHost(source)
        }
    }

    override fun getAllHosts(): Flow<DataResult<Pair<Array<String>, Int>>> {
        return flow {
            emit(getHostsData())
        }.flowOn(Dispatchers.IO)
            .map { it.toResult() }
    }

    private fun getHostsData(): Pair<Array<String>, Int> {
        var savedHost = hostStore.savedHost
        if (savedHost.isNullOrBlank()) {
            savedHost = hostStore.availableHosts.first()
            setHost(savedHost)
        } else {
            setHost(savedHost, false)
        }
        val toTypedArray = hostStore.availableHosts.toTypedArray()
        return toTypedArray to toTypedArray.indexOf(savedHost)
    }

    private suspend fun getFullMovie(movie: Movie): Movie {
        val resultDoc = getSource().getResultDoc(movie)
        val hlsList = getSource().getHlsList(resultDoc)
        val title = getSource().getTitle(resultDoc)
        val movieId = getMovieId(movie)
        return when (val type = getSource().getMovieType(movie)) {
            MovieType.CINEMA -> returnCinema(hlsList, movie, type, movieId, title)
            MovieType.SERIAL -> returnSerial(hlsList, movie, type)
        }
    }

    private fun returnCinema(
        hlsList: String,
        movie: Movie,
        type: MovieType,
        movieId: Int?,
        title: String?
    ): Movie {
        val hlsQualityMap = getSource().getQualityMap(hlsList)
        val selectedQuality = getMinQualityKey(hlsQualityMap)
        return movie.copy(
            type = type,
            title = title ?: "",
            video = Video(
                id = movieId,
                title = title,
                videoUrl = hlsQualityMap[selectedQuality],
                hlsList = hlsQualityMap,
                selectedHls = selectedQuality,
                type = type
            )
        )
    }

    private fun returnSerial(
        hlsList: String,
        movie: Movie,
        type: MovieType
    ): Movie {
        val serialData = getSource().parsingSerialData(hlsList)
        val firstSeason = serialData.seasons?.minByOrNull { it.id ?: 0 }
        val episodes = firstSeason?.episodes ?: emptyList()
        val firstEpisode = episodes.minByOrNull { it.id ?: 0 }
        val hlsQualityMap = firstEpisode?.hlsList
        val selectedQuality = getMinQualityKey(hlsQualityMap)
        updateStore(firstSeason, episodes)
        return movie.copy(
            type = type,
            video = Video(
                id = firstEpisode?.id,
                title = firstEpisode?.title,
                videoUrl = hlsQualityMap?.get(selectedQuality),
                hlsList = hlsQualityMap,
                selectedHls = selectedQuality,
                type = type
            ),
            serialData = serialData
        )
    }

    private fun updateStore(
        firstSeason: SerialSeason?,
        value: List<SerialEpisode>
    ) {
        videoCache.currentSeason = firstSeason
        videoCache.currentEpisode = value.firstOrNull()
    }

    override fun onPlaylistChanged(
        seasonPosition: Int,
        episodePosition: Int
    ): Flow<DataResult<SerialEpisode?>> {
        return flow {
            val serialSeason = currentMovie?.serialData?.seasons?.getOrNull(seasonPosition)
            val value = serialSeason?.episodes ?: emptyList()
            updateStore(serialSeason, value)
            val episode = videoCache.currentSeason?.episodes?.getOrNull(episodePosition)
            emit(episode)
        }.flowOn(Dispatchers.IO)
            .map { it.toResult() }
    }

    private fun getMovieId(movie: Movie): Int? {
        val groupValues = "^(\\d+)-\\b\\w+\\b-.*".toRegex()
            .find(movie.detailUrl?.substringAfter(hostStore.baseUrl).toString())?.groupValues
        return groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun getMinQualityKey(hlsQualityMap: HashMap<String, String>?): String? {
        val keys = hlsQualityMap?.keys
        return keys?.map { it.toIntOrNull() ?: 0 }?.minOrNull()?.toString() ?: keys?.first()
    }
}
