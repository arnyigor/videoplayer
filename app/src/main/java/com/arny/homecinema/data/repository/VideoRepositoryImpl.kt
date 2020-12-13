package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.*
import com.arny.homecinema.data.network.NetworkModule.Companion.VIDEO_BASE_URL
import com.arny.homecinema.data.network.ResponseBodyConverter
import com.arny.homecinema.data.network.VideoApiService
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.Video
import com.arny.homecinema.di.models.VideoSearchLink
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
    private val responseBodyConverter: ResponseBodyConverter
) : VideoRepository {

    override fun searchVideo(search: String): Flow<MutableList<Video>> {
        return flow {
            emit(
                videoApiService.searchVideo(
                    story = search,
                    doAction = "search",
                    subaction = "search",
                    search_start = "0",
                    full_search = "0",
                    result_from = "1"
                )
            )
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                mutableListOf<Video>().apply {
                    for (link in getSearchResultLinks(doc)) {
                        add(getVideoFromLink(link))
                    }
                }
            }
    }

    private fun getSearchResultLinks(doc: Document) =
        doc.getElementById("dle-content")
            .select(".th-item a")

    override fun getAllVideos(): Flow<DataResult<MainPageContent>> {
        return flow { emit(videoApiService.requestMainpage()) }
            .map(::getMainPageContent)
            .flowOn(Dispatchers.IO)
    }

    override fun getAllVideos(type: String?): Flow<DataResult<MainPageContent>> {
        return flow {
            val url = VIDEO_BASE_URL + type?.substringAfter("/")
            emit(videoApiService.requestMainpage(url))
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

    private fun getMainVideos(doc: Document): MutableList<Video> {
        return mutableListOf<Video>().apply {
            for (link in getLinks(doc)) {
                add(getVideoFromLink(link))
            }
        }
    }

    private fun getVideoFromLink(link: Element) =
        Video(link.text(), link.attr("href"), getImgUrl(link))

    private fun getLinks(doc: Document) = doc.body()
        .select(".content").first()
        .select(".sect").first()
        .select(".sect-items").first()
        .select(".th-item a")


    private fun getVideoSearchFromLink(link: Element) =
        VideoSearchLink(link.text(), link.attr("href"))


    private fun getMenuItems(doc: Document) = doc.body()
        .getElementById("header")
        .select(".hmenu li a")

    private fun getImgUrl(link: Element) = link.select(".th-img").first()
        .select("img").first().attr("src").toString()


    @FlowPreview
    override fun loadVideo(video: Video): Flow<DataResult<Video>> {
        return flow {
            emit(getFullVideo(video))
        }.flowOn(Dispatchers.IO)
            .map {
                it.toResult()
            }
    }

    private suspend fun getFullVideo(video: Video): Video {
        val headers = mapOf("Referer" to "${VIDEO_BASE_URL}index.php")
        val body = videoApiService.getVideoDetails(video.infoUrl, headers)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
        val iFrameResponse = getUrlData(iFrameUrl)
        val iframeDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(iframeDoc)
        val hlsList = getHlsList(iframeDoc)
        val serial = getSerialQualityMap(hlsList)
        val hlsQualityMap = getQualityMap(hlsList)
        return video.copy(
            videoUrl = hlsQualityMap["720"]
        )
    }

    private fun getSerialQualityMap(hlsList: String): SerialData {
        val filter = hlsList.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
            .substringAfter("seasons:[{")
            .substringBefore("}]}]")
            .split("\"season\":")
            .filter { it.isNotBlank() }
        val data = SerialData()
        for (hls in filter) {
            val quality = hls.substringBefore(":")
            val link = hls.substringAfter(":")

        }
        return SerialData()
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

    private fun getHlsList(iframeDoc: Document): String {
        val hlsList = iframeDoc
            .getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList
    }

    private fun getIframeUrl(detailsDoc: Document): String? {
        val iFrameUrl = detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box").getOrNull(1)
            ?.select("iframe")?.attr("src")
        return iFrameUrl
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
            .forEach { seasonData ->
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
                    .forEach { episodeData ->
                        val episodeId = episodeData.substring(0,1).toIntOrNull() ?: 0
                        val videoQualityMap = hashMapOf<String, String>()
                        val title = episodeData.substringAfter("\"title\":").replace("\"", "")
                        episodeData
                            .substringAfter("hlsList\":{")
                            .substringBefore("},\"audio\"")
                            .split(",")
                            .asSequence()
                            .map { it.substring(1, it.length - 1).replace("\"", "") }
                            .forEach { hls ->
                                val quality = hls.substringBefore(":")
                                val link = hls.substringAfter(":")
                                if (quality.isNotBlank() && link.isNotBlank()) {
                                    videoQualityMap[quality] = link
                                }
                            }
                        episodes.add(SerialEpisode(episodeId, title, videoQualityMap))
                    }
                seasons.add(SerialSeason(id, episodes))
            }
        return SerialData(seasons)
    }

    private suspend fun getUrlData(url: String?) = videoApiService.getUrlData(
        url,
        mapOf(
            "Accept-Encoding" to "gzip, deflate, br",
            "Host" to "apilordfilms-s.multikland.net",
        )
    )
}
