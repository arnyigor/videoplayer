package com.arny.mobilecinema.data.models

data class HtmlPageData(
    val baseUrl: String,
    val title: String,
    val img: String,
    val info: String,
    val fullDescr: String,
    val frames: List<String>,
    val trailer: String
)
