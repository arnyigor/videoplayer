package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class StreamingData(
    @SerializedName("formats")
    val formats: List<Format?>? = null
)