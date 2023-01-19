package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class Thumbnail(
    @SerializedName("thumbnails")
    val thumbnails: List<ThumbnailX>? = listOf()
)