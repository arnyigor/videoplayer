package com.arny.mobilecinema.presentation.player

data class SegmentsData(
    val hlsBaseUrl: String = "",
    val otherBaseUrl: String = "",
    val segments: List<String> = emptyList()
)
