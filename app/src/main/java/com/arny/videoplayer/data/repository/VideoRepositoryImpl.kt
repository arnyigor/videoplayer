package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.data.models.M3u8Response
import com.arny.videoplayer.data.models.toResult
import com.arny.videoplayer.data.network.ResponseBodyConverter
import com.arny.videoplayer.data.network.VideoApiService
import com.arny.videoplayer.data.utils.fromJson
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

    override fun searchVideo(search: String): Flow<List<Video>> {
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
        .getElementById("owl-top")
        .select(".th-item a")

    private fun getImgUrl(link: Element) =
        "https:" + link.select("img").first().attr("src").toString()


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
        val body = videoApiService.getVideoDetails(video.url)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain")
            .first()
            .select("iframe").attr("src")
        val iFrameBody = videoApiService.getIframeData(iFrameUrl)
        val iframeDataBody = responseBodyConverter.convert(iFrameBody)
        requireNotNull(iframeDataBody)
        val index = "index.m3u8"
        val m3u8Result = "https://" + iframeDataBody.body()
            .getElementById("nativeplayer")
            .attr("data-config")
            .fromJson(M3u8Response::class.java)
            ?.hls
            ?.substringAfter("//")
            ?.substringBeforeLast("/$index") + "/720/$index"
        println(m3u8Result)
        return video.copy(
            playUrl = m3u8Result
        )
    }
}
