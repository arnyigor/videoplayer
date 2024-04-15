package com.arny.mobilecinema.domain.models

data class DownloadManagerData(
    val isInitValid: Boolean = false,
    val downloadsEmpty: Boolean = false,
    val isEqualsLinks: Boolean = false,
    val movieTitle: String = "",
    val downloadPercent: Float = 0.0f,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
)
