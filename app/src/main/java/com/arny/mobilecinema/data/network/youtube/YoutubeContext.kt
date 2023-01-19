package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class YoutubeContext(
    @SerializedName("client") val client: YoutubeClient = YoutubeClient(),
    @SerializedName("request") val request: YoutubeRequest = YoutubeRequest(),
    @SerializedName("user") val user: YoutubeUser = YoutubeUser()
)