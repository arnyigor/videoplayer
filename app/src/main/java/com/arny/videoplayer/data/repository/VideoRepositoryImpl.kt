package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.network.VideoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jsoup.Jsoup
import javax.inject.Inject


class VideoRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService
) : VideoRepository {
    private companion object {
        const val SEARCH_RESULT_CONTENT_ID = "dle-content"
        const val SEARCH_RESULT_LINKS = "div.th-item a"
    }

    override fun searchVideo(): Flow<String> {
        return flow {
            val requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("story", "мстители")
                .build()
            emit(videoApiService.searchVideo(requestBody))
        }.flowOn(Dispatchers.IO)
            .map {
                val doc = Jsoup.parse(it)
                val searchResult = doc.getElementById(SEARCH_RESULT_CONTENT_ID)
                val links = searchResult.select(SEARCH_RESULT_LINKS)
                val results = links.size
                println(results)
                println(links)
                results.toString()
            }
    }
}