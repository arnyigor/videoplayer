package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UrlData(
    val duration: String? = null,
    val file: String? = null,
    val poster: String? = null,
    val url: String? = null,
    val urls: List<String> = emptyList()
) : Parcelable