package com.arny.homecinema.di.models

data class MainPageContent(
    val movies: List<Movie>? = null,
    val searchVideoLinks: List<VideoSearchLink>? = null
)
