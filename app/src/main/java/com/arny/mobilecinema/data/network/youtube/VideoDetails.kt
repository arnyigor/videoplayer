package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class VideoDetails(
    @SerializedName("author")
    val author: String? = "",
    @SerializedName("channelId")
    val channelId: String? = "",
    @SerializedName("isOwnerViewing")
    val isOwnerViewing: Boolean? = false,
    @SerializedName("lengthSeconds")
    val lengthSeconds: String? = "",
    @SerializedName("shortDescription")
    val shortDescription: String? = "",
    @SerializedName("thumbnail")
    val thumbnail: Thumbnail? = Thumbnail(),
    @SerializedName("title")
    val title: String? = "",
    @SerializedName("videoId")
    val videoId: String? = ""
)