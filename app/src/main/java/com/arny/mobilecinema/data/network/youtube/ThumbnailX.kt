package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class ThumbnailX(
    @SerializedName("height")
    val height: Int? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("width")
    val width: Int? = null
)