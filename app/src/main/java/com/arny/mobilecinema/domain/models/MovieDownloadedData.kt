package com.arny.mobilecinema.domain.models

data class MovieDownloadedData(
    val downloadedPercent: Float = 0.0f,
    val downloadedSize: Long = 0L,
    val loading: Boolean = false
)
