package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.network.HostStore
import com.arny.homecinema.data.network.IHostStore
import com.arny.homecinema.data.network.ResponseBodyConverter
import com.arny.homecinema.data.network.docparser.IDocumentParserFactory
import com.arny.homecinema.data.network.headers.IHeadersFactory
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
    private val parserFactory: IDocumentParserFactory,
    private val headersFactory: IHeadersFactory,
) : VideoRepository {

    override fun searchMovie(search: String): Flow<MutableList<Movie>> {
        return flow {
            emit(
                videoApiService.searchVideo(
                    story = search,
                )
            )
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                // TODO: 15.12.2020 добавить тип сериал или фильм
                mutableListOf<Movie>().apply {
                    for (link in getSearchResultLinks(doc)) {
                        add(getVideoFromLink(link))
                    }
                }
            }
    }

    private fun getSearchResultLinks(doc: Document) = parserFactory.createDocumentParser(hostStore)
        .getSearchResultLinks(doc)

    override fun getAllVideos(): Flow<DataResult<MainPageContent>> {
        // TODO перед этим выбрать host
        hostStore.host = HostStore.LORDFILM_AL_HOST
        return flow {
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
        return MainPageContent(getMainVideos(doc), getSearchLInks(doc)).toResult()
    }

    private fun getSearchLInks(doc: Document): MutableList<VideoSearchLink> {
        return mutableListOf<VideoSearchLink>().apply {
            for (link in getMenuItems(doc)) {
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

    private fun getVideoFromLink(link: Element) =
        Movie(link.text(), MovieType.CINEMA, link.attr("href"), getImgUrl(link))

    private fun getMainPageLinks(doc: Document) = parserFactory
        .createDocumentParser(hostStore)
        .getMainPageLinks(doc)

    private fun getVideoSearchFromLink(link: Element) =
        VideoSearchLink(link.text(), link.attr("href"))

    private fun getMenuItems(doc: Document) = parserFactory.createDocumentParser(hostStore)
        .getMenuItems(doc)

    private fun getImgUrl(link: Element) = parserFactory.createDocumentParser(hostStore)
        .getImgUrl(link, hostStore.baseUrl)

    @FlowPreview
    override fun loadMovie(movie: Movie): Flow<DataResult<Movie>> {
        return flow {
            emit(getFullMovie(movie))
        }.flowOn(Dispatchers.IO)
            .map { it.toResult() }
    }

    private suspend fun getFullMovie(movie: Movie): Movie {
        val resultDoc = getResultDoc(movie)
        val hlsList = getHlsList(resultDoc)
        val movieId = getMovieId(movie)
        return when (val type = getMovieType(movie)) {
            MovieType.CINEMA -> {
                val hlsQualityMap = getQualityMap(hlsList)
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
                val serialData = parsingSerialData(hlsList)
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

    private suspend fun getResultDoc(movie: Movie): Document {
        val headers = headersFactory.createHeaders(hostStore).detailHeaders
        val body = videoApiService.getVideoDetails(movie.detailUrl, headers)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
        val iFrameResponse = videoApiService.getUrlData(
            iFrameUrl,
            headersFactory.createHeaders(hostStore).iFrameHeaders
        )
        val resultDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(resultDoc)
        return resultDoc
    }

    private fun getMovieType(movie: Movie): MovieType {
        val link = movie.detailUrl?.substringAfter("//")?.substringAfter("/") ?: ""
        return when {
            link.contains("-film-") -> MovieType.CINEMA
            link.contains("-serial-") -> MovieType.SERIAL
            else -> MovieType.CINEMA
        }
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

    private fun getQualityMap(hlsList: String): HashMap<String, String> {
        val hlss = hlsList
            .replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), "")
            .substringAfter("hlsList:{")
            .substringBefore("}")
            .split(",")
            .map { it.substring(1, it.length - 1).replace("\"", "") }
        val videoQualityMap = hashMapOf<String, String>()
        for (hls in hlss) {
            val quality = hls.substringBefore(":")
            val link = hls.substringAfter(":")
            if (quality.isNotBlank() && link.isNotBlank()) {
                videoQualityMap[quality] = link
            }
        }
        return videoQualityMap
    }

    private fun getHlsList(doc: Document): String =
        parserFactory.createDocumentParser(hostStore).getHlsList(doc)

    private fun getIframeUrl(detailsDoc: Document): String? {
        return parserFactory.createDocumentParser(hostStore).getIframeUrl(detailsDoc)
    }

    private fun parsingSerialData(hlsList: String): SerialData {
        val seasons = mutableListOf<SerialSeason>()
        hlsList.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
            .substringAfter("seasons:[{")
            .substringBefore("}]}]")
            .split("\"season\":")
            .asSequence()
            .filter { it.isNotBlank() }
            .forEach { seasonData -> fillSeason(seasonData, seasons) }
        seasons.sortBy { it.id }
        return SerialData(seasons)
    }

    private fun fillSeason(
        seasonData: String,
        seasons: MutableList<SerialSeason>
    ) {
        val seasonIdEnd = seasonData.indexOf(",\"blocked\"")
        val id = seasonData.substring(0, seasonIdEnd).toIntOrNull() ?: 0
        val episodes = mutableListOf<SerialEpisode>()
        seasonData.substringAfter("episodes\":")
            .substringAfter("[{\"")
            .substringBeforeLast("]")
            .split("episode\":\"")
            .asSequence()
            .filterNot { it.isBlank() }
            .map { it.substringBeforeLast("},{\"") }
            .forEach { episodeData -> fillEpisode(episodeData, episodes) }
        episodes.sortBy { it.id }
        seasons.add(SerialSeason(id, episodes))
    }

    private fun fillEpisode(
        episodeData: String,
        episodes: MutableList<SerialEpisode>
    ) {
        val episodeId = episodeData.substring(0, 1).toIntOrNull() ?: 0
        val videoQualityMap = hashMapOf<String, String>()
        val title = episodeData.substringAfter("\"title\":").replace("\"", "")
        episodeData
            .substringAfter("hlsList\":{")
            .substringBefore("},\"audio\"")
            .split(",")
            .asSequence()
            .map { it.substring(1, it.length - 1).replace("\"", "") }
            .forEach { hls -> fillQualityMap(hls, videoQualityMap) }
        episodes.add(SerialEpisode(episodeId, title, videoQualityMap))
    }

    private fun fillQualityMap(
        hls: String,
        videoQualityMap: HashMap<String, String>
    ) {
        val quality = hls.substringBefore(":")
        val link = hls.substringAfter(":")
        if (quality.isNotBlank() && link.isNotBlank()) {
            videoQualityMap[quality] = link
        }
    }

}
