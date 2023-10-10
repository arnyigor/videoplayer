package com.arny.mobilecinema.data.db.models

import com.arny.mobilecinema.domain.models.MovieType

data class MovieUpdate(
    val dbId: Long = 0,
    val pageUrl: String = "",
    val title: String = "",
    val updated: Long = 0,
    val genre: String = "",
    val type: Int = MovieType.NO_TYPE.value,
    val cinemaUrls: String = "",
)
