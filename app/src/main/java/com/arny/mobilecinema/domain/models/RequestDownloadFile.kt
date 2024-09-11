package com.arny.mobilecinema.domain.models

data class RequestDownloadFile(
    val url: String,
    val fileName: String,
    val title: String,
    val isMp4: Boolean,
)
