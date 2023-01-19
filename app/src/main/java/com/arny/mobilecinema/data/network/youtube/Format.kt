package com.arny.mobilecinema.data.network.youtube


import com.google.gson.annotations.SerializedName

data class Format(
    @SerializedName("approxDurationMs")
    val approxDurationMs: String? = null,
    @SerializedName("audioChannels")
    val audioChannels: Int? = null,
    @SerializedName("audioQuality")
    val audioQuality: String? = null,
    @SerializedName("audioSampleRate")
    val audioSampleRate: String? = null,
    @SerializedName("averageBitrate")
    val averageBitrate: Int? = null,
    @SerializedName("bitrate")
    val bitrate: Int? = null,
    @SerializedName("contentLength")
    val contentLength: String? = null,
    @SerializedName("fps")
    val fps: Int? = null,
    @SerializedName("height")
    val height: Int? = null,
    @SerializedName("itag")
    val itag: Int? = null,
    @SerializedName("lastModified")
    val lastModified: String? = null,
    @SerializedName("mimeType")
    val mimeType: String? = null,
    @SerializedName("projectionType")
    val projectionType: String? = null,
    @SerializedName("quality")
    val quality: String? = null,
    @SerializedName("qualityLabel")
    val qualityLabel: String? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("width")
    val width: Int? = null
)