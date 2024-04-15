package com.arny.mobilecinema.domain.models

data class MovieDownload(
    val pageUrl: String,
    val downloadUrl: String,
    val percent: Float
)
