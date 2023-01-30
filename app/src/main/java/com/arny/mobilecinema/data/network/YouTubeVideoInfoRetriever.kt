package com.arny.mobilecinema.data.network

import com.arny.mobilecinema.data.network.youtube.MainAppWebInfo
import com.arny.mobilecinema.data.network.youtube.YoutubeClient
import com.arny.mobilecinema.data.network.youtube.YoutubeContext
import com.arny.mobilecinema.data.network.youtube.YoutubeRequestBody
import com.arny.mobilecinema.data.network.youtube.YoutubeVideoData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeVideoInfoRetriever constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val URL_YOUTUBE_PLAYER_LINK =
            "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val URL_YOUTUBE_GET_VIDEO_INFO =
            "http://www.youtube.com/get_video_info?&video_id="
    }

    suspend fun retrieve(videoId: String) = withContext(Dispatchers.IO) {
        val webInfo = MainAppWebInfo("/watch?v=$videoId")
        val youtubeClient = YoutubeClient(mainAppWebInfo = webInfo)
        val requestBody = YoutubeRequestBody(
            videoId = videoId,
            context = YoutubeContext(youtubeClient)
        )
        httpClient.post(URL_YOUTUBE_PLAYER_LINK) { setBody(requestBody) }
            .body<YoutubeVideoData>()
    }
}