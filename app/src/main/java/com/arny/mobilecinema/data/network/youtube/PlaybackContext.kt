package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class PlaybackContext(
    @SerializedName("contentPlaybackContext")
    val contentPlaybackContext: ContentPlaybackContext = ContentPlaybackContext()
)