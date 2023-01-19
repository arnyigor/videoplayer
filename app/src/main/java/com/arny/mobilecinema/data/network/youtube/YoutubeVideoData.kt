package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class YoutubeVideoData(
    @SerializedName("streamingData")
    val streamingData: StreamingData? = StreamingData(),
    @SerializedName("videoDetails")
    val videoDetails: VideoDetails? = VideoDetails()
)