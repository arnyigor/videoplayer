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
import javax.inject.Inject


class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter
) : VideoRepository {
    private companion object {
        const val SEARCH_RESULT_CONTENT_ID = "dle-content"
        const val SEARCH_RESULT_LINKS = "div.th-item a"
    }

    override fun searchVideo(search: String): Flow<String> {
        return flow {
            val value = videoApiService.searchVideo(search)
            emit(value)
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                val searchResult = doc.getElementById(SEARCH_RESULT_CONTENT_ID)
                val links = searchResult.select(SEARCH_RESULT_LINKS)
                val results = links.size
                results.toString()
            }
    }

    override fun getAllVideos(): Flow<List<Video>> {
        return flow {
            emit(videoApiService.requestMainpage())
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val doc = responseBodyConverter.convert(body)
                requireNotNull(doc)
                val links = doc.body()
                    .getElementById("owl-top")
                    .select(".th-item a")
                mutableListOf<Video>().apply {
                    for (link in links) {
                        val imgUrl = "https:" + link.select("img").first().attr("src").toString()
                        add(Video(link.text(), link.attr("href"), imgUrl))
                    }
                }
            }
    }


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
