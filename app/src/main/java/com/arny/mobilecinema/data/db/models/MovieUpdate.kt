package com.arny.mobilecinema.data.db.models

data class MovieUpdate(
    val dbId: Long = 0,
    override val pageUrl: String = "",
    val title: String = "",
    val updated: Long = 0,
    val genre: String = "",
) : IMovieUpdate
