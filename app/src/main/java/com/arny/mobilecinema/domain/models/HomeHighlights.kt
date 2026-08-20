package com.arny.mobilecinema.domain.models

data class HomeHighlights(
    val recent: List<ViewMovie> = emptyList(),
    val bestNow: List<ViewMovie> = emptyList(),
    val forYou: List<ViewMovie> = emptyList()
)
