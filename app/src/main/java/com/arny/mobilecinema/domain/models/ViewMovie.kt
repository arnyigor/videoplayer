package com.arny.mobilecinema.domain.models

data class ViewMovie constructor(
    val dbId: Long = 0,
    val title: String = "",
    val type: Int = 0,
    val img: String = "",
)
