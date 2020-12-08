package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.network.VideoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.ResponseBody
import okio.Buffer
import okio.GzipSource
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.Inject


class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService
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
                val result = responseToStringConvert(body)
                val doc = Jsoup.parse(result)
                val searchResult = doc.getElementById(SEARCH_RESULT_CONTENT_ID)
                val links = searchResult.select(SEARCH_RESULT_LINKS)
                val results = links.size
                results.toString()
            }
    }

    override fun all(): Flow<String> {
        return flow {
            emit(videoApiService.requestMainpage())
        }.flowOn(Dispatchers.IO)
            .map { body ->
                val result = responseToStringConvert(body)
                val doc = Jsoup.parse(result)
                val links = doc.body()
                    .getElementById("owl-top")
                    .select(".th-item a")
                StringBuilder().apply {
                    for (link in links) {
                        append(link.text())
                        append("\n")
                        append(link.attr("href"))
                        append("\n")
                    }
                }.toString()
            }
    }

    private fun responseToStringConvert(res: ResponseBody): String {
        val source = res.source()
        val origin = source.buffer
        var clone = origin.clone()
        GzipSource(clone.clone()).use { gzippedResponseBody ->
            clone = Buffer()
            clone.writeAll(gzippedResponseBody)
        }
        return clone.readString(UTF_8)
    }
}