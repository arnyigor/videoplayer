package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName

@Parcelize
data class AnwapUrl(
    @SerialName("duration") val duration: String? = null,
    @SerialName("file") val file: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("url") val url: String? = null,
    val urls: List<String> = emptyList()
) : Parcelable