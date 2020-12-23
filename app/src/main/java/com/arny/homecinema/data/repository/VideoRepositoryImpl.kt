package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.network.sources.IVideoSourceFactory
import com.arny.homecinema.data.repository.sources.PREFS_CONSTANTS
import com.arny.homecinema.data.repository.sources.Prefs
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter,
    private val hostStore: IHostStore,
    private val sourceFactory: IVideoSourceFactory,
    private val prefs: Prefs,
) : VideoRepository {
    private val moviesStore = MoviesStore

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
        return MainPageContent(getMainVideos(doc), getMenuLinks()).toResult()
    }

    private fun getMenuLinks(): MutableList<VideoSearchLink> {
        val menuItems = emptyList<Element>()//getSource().getMenuItems(doc)
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

    override fun loadMovie(movie: Movie, cache: Boolean): Flow<DataResult<Movie>> {
        return flow {
            val movieInStore = moviesStore.movies.find { it.detailUrl == movie.detailUrl }
            val movie1 = if (movieInStore != null && cache) {
                currentMovie = movieInStore
                currentMovie!!
            } else {
                val value = getFullMovie(movie)
                if (cache && !value.video?.videoUrl.isNullOrBlank()) {
                    moviesStore.movies.add(value)
                }
                currentMovie = value
                currentMovie!!
            }
            emit(movie1.toResult())
        }.flowOn(Dispatchers.IO)
    }

    override fun setHost(source: String, resetHost: Boolean) {
        hostStore.host = source
        if (resetHost) {
            prefs.put(PREFS_CONSTANTS.PREF_CURRENT_HOST, source)
        }
    }

    override fun getAllHosts(): Flow<DataResult<Pair<Array<String>, Int>>> {
        return flow {
            emit(getHostsData())
        }.flowOn(Dispatchers.IO)
            .map { it.toResult() }
    }

    private fun getHostsData(): Pair<Array<String>, Int> {
        var currentHost = prefs.get<String>(PREFS_CONSTANTS.PREF_CURRENT_HOST)
        if (currentHost.isNullOrBlank()) {
            currentHost = hostStore.availableHosts.first()
            setHost(currentHost)
        } else {
            setHost(currentHost, false)
        }
        val toTypedArray = hostStore.availableHosts.toTypedArray()
        return toTypedArray to toTypedArray.indexOf(currentHost)
    }

    private suspend fun getFullMovie(movie: Movie): Movie {
        val hlsList = getSource().getHlsList(movie)
        val movieId = getMovieId(movie)
        return when (val type = getSource().getMovieType(movie)) {
            MovieType.CINEMA -> {
                val hlsQualityMap = getSource().getQualityMap(hlsList)
                val selectedQuality = getMinQualityKey(hlsQualityMap)
                movie.copy(
                    type = type,
                    video = Video(
                        id = movieId,
                        videoUrl = hlsQualityMap[selectedQuality],
                        hlsList = hlsQualityMap,
                        selectedHls = selectedQuality
                    )
                )
            }
            MovieType.SERIAL -> {
                val serialData = getSource().parsingSerialData(hlsList)
                val firstSeason = serialData.seasons?.minByOrNull { it.id ?: 0 }
                val firstEpisode = firstSeason?.episodes?.minByOrNull { it.id ?: 0 }
                val hlsQualityMap = firstEpisode?.hlsList
                val selectedQuality = getMinQualityKey(hlsQualityMap)
                movie.copy(
                    type = type,
                    video = Video(
                        id = firstEpisode?.id,
                        videoUrl = hlsQualityMap?.get(selectedQuality),
                        hlsList = hlsQualityMap,
                        selectedHls = selectedQuality
                    ),
                    serialData = serialData
                )
            }
        }
    }

    override fun onSeasonChanged(position: Int): Flow<DataResult<List<SerialEpisode>>> {
        return flow {
            val serialSeason = currentMovie?.serialData?.seasons?.getOrNull(position)
            val value = serialSeason?.episodes ?: emptyList()
            moviesStore.currentSeason = serialSeason
            moviesStore.currentEpisode = value.firstOrNull()
            emit(value)
        }.flowOn(Dispatchers.IO)
            .map { it.toResult() }
    }

    override fun onEpisodeChanged(position: Int): Flow<DataResult<SerialEpisode?>> {
        return flow {
            emit(moviesStore.currentSeason?.episodes?.getOrNull(position))
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
