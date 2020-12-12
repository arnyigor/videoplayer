package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.data.models.toResult
import com.arny.videoplayer.data.network.NetworkModule.Companion.VIDEO_BASE_URL
import com.arny.videoplayer.data.network.ResponseBodyConverter
import com.arny.videoplayer.data.network.VideoApiService
import com.arny.videoplayer.di.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    override fun getAllVideos(): Flow<List<Video>> {
        return flow {
            emit(videoApiService.requestMainpage())
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                mutableListOf<Video>().apply {
                    for (link in getLinks(doc)) {
                        add(getVideoFromLink(link))
                    }
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
        val body = videoApiService.getVideoDetails(video.url, headers)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
        val iFrameResponse = getUrlData(iFrameUrl)
        val iframeDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(iframeDoc)
        val hlsList = getHlsList(iframeDoc)
        val hlsQualityMap = getQualityMap(hlsList)
        return video.copy(
            playUrl = hlsQualityMap["720"]
        )
    }

    suspend fun getVideoByQuality(qualityUrl: String?) {
        val urlData = getUrlData(qualityUrl)

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


    private suspend fun getUrlData(url: String?) = videoApiService.getUrlData(
        url,
        mapOf(
            "Accept-Encoding" to "gzip, deflate, br",
            "Host" to "apilordfilms-s.multikland.net",
        )
    )
}
