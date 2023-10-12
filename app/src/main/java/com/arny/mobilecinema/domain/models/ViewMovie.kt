package com.arny.mobilecinema.domain.models

data class ViewMovie(
    val dbId: Long = 0,
    val title: String = "",
    val type: Int = 0,
    val img: String = "",
    val year: Int = 0,
    val likes: Int = 0,
    val dislikes: Int = 0,
)
