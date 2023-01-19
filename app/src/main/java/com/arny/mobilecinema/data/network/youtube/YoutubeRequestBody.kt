package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class YoutubeRequestBody(
    @SerializedName("contentCheckOk") val contentCheckOk: Boolean = false,
    @SerializedName("context") val context: YoutubeContext = YoutubeContext(),
    @SerializedName("playbackContext") val playbackContext: PlaybackContext = PlaybackContext(),
    @SerializedName("racyCheckOk") val racyCheckOk: Boolean = false,
    @SerializedName("videoId") val videoId: String
)