package com.arny.mobilecinema.domain.models

data class DownloadMovieItem(
    val pageUrl: String,
    val downloadUrl: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0
)
