package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class YoutubeRequest(
    @SerializedName("consistencyTokenJars") val consistencyTokenJars: List<String?>? = emptyList(),
    @SerializedName("internalExperimentFlags") val internalExperimentFlags: List<String?>? = emptyList(),
    @SerializedName("useSsl") val useSsl: Boolean = true
)