package com.arny.mobilecinema.data.models

import com.google.gson.annotations.SerializedName

data class AnwapMovieData(
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("file") val fileUrl: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("url") val url: String? = null
)