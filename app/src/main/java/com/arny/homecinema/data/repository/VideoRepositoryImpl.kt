package com.arny.homecinema.data.repository

import android.content.Context
import android.net.Uri
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.DataThrowable
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.network.sources.IVideoSourceFactory
import com.arny.homecinema.data.repository.sources.cache.VideoCache
import com.arny.homecinema.data.repository.sources.store.StoreProvider
import com.arny.homecinema.data.utils.FilePathUtils
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter,
    private val hostStore: IHostStore,
    private val sourceFactory: IVideoSourceFactory,
    private val storeProvider: StoreProvider,
    private val videoCache: VideoCache,
    private val context: Context,
) : VideoRepository {

    @Volatile
    private var currentMovie: Movie? = null

    override fun searchMovie(search: String): Flow<MutableList<Movie>> {
        return flow {
            val doc = videoApiService.searchVideo(
                getSource().searchUrl,
                getSource().getSearchFields(search),
                getSource().searchHeaders,
            ).convertToDoc()
            val list = mutableListOf<Movie>().apply {
                for (link in getSearchResultLinks(doc)) {
                    add(getVideoFromLink(link))
                }
            }
            emit(list)
        }.flowOn(Dispatchers.IO)
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
            val doc = videoApiService.requestMainPage(
                hostStore.baseUrl,
                getSource().addMainPageHeaders + hostStore.mainPageHeaders
            )
                .convertToDoc()
            emit(getMainPageContent(doc))
        }
            .flowOn(Dispatchers.IO)
    }

    override fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>> {
        return flow {
            val url = hostStore.baseUrl + type?.substringAfter("/")
            val doc = videoApiService.requestTyped(url).convertToDoc()
            emit(getMainPageContent(doc))
        }
            .flowOn(Dispatchers.IO)
    }

    private fun ResponseBody.convertToDoc(): Document {
        val doc = responseBodyConverter.convert(this)
        requireNotNull(doc)
        return doc
    }

    private fun getMainPageContent(doc: Document): DataResult<MainPageContent> {
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

    override fun clearCache(movie: Movie?): Flow<DataResult<Boolean>> {
        return flow {
            if (movie != null) {
                videoCache.removeFromCache(movie)
                storeProvider.removeFromSaved(movie)
                emit(true.toResult())
            } else {
                emit(false.toResult())
            }
        }.flowOn(Dispatchers.IO)
    }

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

    @FlowPreview
    override fun getAllCached(): Flow<DataResult<List<Movie>>> {
        return flow {
            emit(storeProvider.allMovies().toResult())
        }.flowOn(Dispatchers.IO)
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
        val videoUrl = movie.video?.videoUrl ?: ""
        return when (movie.type) {
            MovieType.CINEMA, MovieType.SERIAL -> getRemoteContent(movie)
            MovieType.CINEMA_LOCAL, MovieType.SERIAL_LOCAL -> getSDContent(movie, videoUrl)
        }
    }

    private fun getSDContent(
        movie: Movie,
        videoUrl: String
    ): Movie {
        val copy = movie.copy(
            selectedQuality = "720"
        )
        return when (val type = movie.type) {
            MovieType.CINEMA_LOCAL -> returnCinema(
                copy,
                movie.type,
                1,
                movie.title,
                hashMapOf(
                    "720" to videoUrl
                )
            )
            MovieType.SERIAL_LOCAL -> returnSerial(copy, type, getSDSerialData(videoUrl))
            MovieType.CINEMA, MovieType.SERIAL -> throw IllegalStateException()
        }
    }

    private fun getSDSerialData(videoUrl: String): SerialData {
        val uri = Uri.parse(videoUrl)
        val path = FilePathUtils.getPath(uri, context)
        if (!path.isNullOrBlank()) {
            throw DataThrowable(R.string.video_url_not_found)
        }
        var index = 1
        val episodes = mutableListOf<SerialEpisode>()
        File(path!!).listFiles()?.toList()?.asSequence()?.forEach {
            val serialEpisode = SerialEpisode(
                id = index,
                title = it.name,
                hlsList = hashMapOf(
                    "720" to Uri.fromFile(it).toString()
                )
            )
            episodes.add(serialEpisode)
            index++
        }
        episodes.sortBy { it.id }
        return SerialData(listOf(SerialSeason(1, episodes)))
    }

    private suspend fun getRemoteContent(movie: Movie): Movie {
        val resultDoc = getSource().getResultDoc(movie)
        val hlsList = getSource().getHlsList(resultDoc)
        val title = getSource().getTitle(resultDoc)
        val movieId = getMovieId(movie)
        val qualityMap = getSource().getQualityMap(hlsList)
        return when (val type = getSource().getMovieType(movie)) {
            MovieType.CINEMA -> returnCinema(
                movie,
                type,
                movieId,
                title,
                qualityMap
            )
            MovieType.SERIAL -> returnSerial(
                movie,
                type,
                getSource().parsingSerialData(hlsList)
            )
            MovieType.CINEMA_LOCAL, MovieType.SERIAL_LOCAL -> throw IllegalStateException()
        }
    }

    private fun returnCinema(
        movie: Movie,
        type: MovieType,
        movieId: Int?,
        title: String?,
        hlsQualityMap: HashMap<String, String>
    ): Movie {
        val selectedQuality = if (movie.selectedQuality.isNullOrBlank()) {
            getMinQualityKey(hlsQualityMap)
        } else {
            movie.selectedQuality
        }
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
        movie: Movie,
        type: MovieType,
        serialData: SerialData
    ): Movie {
        val firstSeason = serialData.seasons?.minByOrNull { it.id ?: 0 }
        val episodes = firstSeason?.episodes ?: emptyList()
        val firstEpisode = episodes.minByOrNull { it.id ?: 0 }
        val hlsQualityMap = firstEpisode?.hlsList
        val selectedQuality = if (movie.selectedQuality.isNullOrBlank()) {
            getMinQualityKey(hlsQualityMap)
        } else {
            movie.selectedQuality
        }
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
