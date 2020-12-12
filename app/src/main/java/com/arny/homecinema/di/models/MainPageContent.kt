package com.arny.homecinema.di.models

data class MainPageContent(
    val videos: List<Video>? = null,
    val searchVideoLinks: List<VideoSearchLink>? = null
)
