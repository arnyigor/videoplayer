package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.network.ResponseBodyConverter
import com.arny.videoplayer.data.network.VideoApiService
import com.arny.videoplayer.di.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.jsoup.Jsoup
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
                val doc = Jsoup.parse(responseBodyConverter.convert(body))
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
                val doc = Jsoup.parse(responseBodyConverter.convert(body))
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
}
