package com.arny.mobilecinema.data.db.models

data class MovieUpdate(
    val dbId: Long = 0,
    val pageUrl: String = "",
    val title: String = "",
    val updated: Long = 0,
)
