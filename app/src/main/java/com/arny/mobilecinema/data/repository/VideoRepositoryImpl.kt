package com.arny.mobilecinema.data.repository

import android.content.Context
import android.net.Uri
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.network.hosts.HostStoreImpl
import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.network.sources.IVideoSourceFactory
import com.arny.mobilecinema.data.repository.sources.cache.VideoCache
import com.arny.mobilecinema.data.repository.sources.store.StoreProvider
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.SerialData
import com.arny.mobilecinema.di.models.SerialEpisode
import com.arny.mobilecinema.di.models.SerialSeason
import com.arny.mobilecinema.di.models.Video
import com.arny.mobilecinema.di.models.VideoApiService
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.domain.models.HostsData
import com.arny.mobilecinema.domain.repository.VideoRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
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

    override fun searchMovie(search: String): Flow<DataResult<MainPageContent>> = doAsync {
        val doc = videoApiService.postRequest(
            getSource().searchUrl,
            getSource().getSearchFields(search),
            getSource().searchHeaders,
        ).convertToDoc()
        val list = mutableListOf<Movie>().apply {
            for (link in getSearchResultLinks(doc)) {
                add(getVideoFromLink(link))
            }
        }
        MainPageContent(list)
    }

    private fun getSearchResultLinks(doc: Document) = getSource().getSearchResultLinks(doc)

    private fun getSource() = sourceFactory.createSource(
        hostStore,
        videoApiService,
        responseBodyConverter
    )

    override fun getAllVideos(): Flow<DataResult<MainPageContent>> = doAsync {
        getHostsData()
        val allMovies = storeProvider.allMovies()
        updateCache(allMovies)
        val doc = if (hostStore.host == HostStoreImpl.HOST_MOCK) {
            null
        } else {
            getSource().requestMainPage().convertToDoc()
        }
        getMainPageContent(doc)
    }

    override fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>> = doAsync {
        val url = hostStore.baseUrl + type?.substringAfter("/")
        val doc = videoApiService.getRequest(url, hostStore.baseHeaders).convertToDoc()
        getMainPageContent(doc)
    }

    private fun ResponseBody.convertToDoc(): Document {
        val doc = responseBodyConverter.convert(this, charset = getSource().getCharset())
        requireNotNull(doc) {
            "Ошибка парсинга документа"
        }
        return doc
    }

    private suspend fun getMainPageContent(doc: Document?): MainPageContent =
        MainPageContent(getMainVideos(doc), getMenuLinks(doc))

    private fun getMenuLinks(doc: Document?): MutableList<VideoMenuLink> {
        val menuItems = getSource().getMenuItems(doc)
        return mutableListOf<VideoMenuLink>().apply {
            for (link in menuItems) {
                add(getVideoMenuFromLink(link))
            }
        }
    }

    private suspend fun getMainVideos(doc: Document?): MutableList<Movie> {
        val mainPageLinks = getSource().getMainPageLinks(doc)
        return mutableListOf<Movie>().apply {
            for (link in mainPageLinks) {
                add(getVideoFromLink(link))
            }
        }
    }

    private fun getVideoFromLink(link: Element): Movie = getSource().getMovieFromLink(link)

    private fun getVideoMenuFromLink(link: Element) = getSource().getMenuVideoLink(link)

    override fun clearCache(movie: Movie?): Flow<DataResult<Boolean>> = doAsync {
        if (movie != null) {
            videoCache.removeFromCache(movie)
            storeProvider.removeFromSaved(movie)
            true
        } else {
            false
        }
    }

    override fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>> = doAsync {
        if (movie != null) {
            videoCache.addToCache(movie)
            if (storeProvider.canSaveToStore()) {
                storeProvider.saveToStore(movie)
            }
            true
        } else {
            false
        }
    }

    @FlowPreview
    override fun searchCached(searchText: String): Flow<DataResult<List<Movie>>> = doAsync {
        var movies = videoCache.searchFromCache(searchText)
        if (movies.isEmpty() && isSaveToStore()) {
            movies = try {
                storeProvider.searchMovies(searchText)
            } catch (e: Exception) {
                emptyList()
            }
        }
        movies
    }

    @FlowPreview
    override fun getAllCached(): Flow<DataResult<List<Movie>>> = doAsync {
        storeProvider.allMovies().apply { updateCache(this) }
    }

    private fun updateCache(allMovies: List<Movie>) {
        allMovies.forEach {
            videoCache.addToCache(it)
        }
    }

    private fun isSaveToStore(): Boolean = storeProvider.canSaveToStore()

    override fun loadMovie(movie: Movie): Flow<DataResult<Movie>> = doAsync {
        val movieInStore = videoCache.getFromCache(movie.title)
        if (movieInStore != null) {
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

    override fun getAllHosts(): Flow<DataResult<HostsData>> = doAsync {
        getHostsData()
    }

    private fun getHostsData(): HostsData {
        var savedHost = hostStore.getCurrentHost()
        if (savedHost.isNullOrBlank()) {
            savedHost = hostStore.availableHosts.first()
            setHost(savedHost)
        } else {
            setHost(savedHost, false)
        }
        val hosts = hostStore.availableHosts
        return HostsData(hosts, hosts.indexOf(savedHost))
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
        val detailsDoc = getSource().getDetailsDoc(movie)
        val resultDoc = getSource().getVideoDoc(detailsDoc)
        val hlsList = getSource().getHlsList(resultDoc)
        val title = getSource().getTitle(detailsDoc, movie)
        val movieId = getMovieId(movie)
        return when (val type = getSource().getMovieType(movie)) {
            MovieType.CINEMA -> returnCinema(
                movie,
                type,
                movieId,
                title,
                getSource().getQualityMap(hlsList)
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
    ): Flow<DataResult<SerialEpisode>> = doAsync {
        val serialSeason = currentMovie?.serialData?.seasons?.getOrNull(seasonPosition)
        val value = serialSeason?.episodes ?: emptyList()
        updateStore(serialSeason, value)
        videoCache.currentSeason?.episodes?.getOrNull(episodePosition)
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
