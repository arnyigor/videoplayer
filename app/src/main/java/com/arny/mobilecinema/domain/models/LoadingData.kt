package com.arny.mobilecinema.domain.models

data class LoadingData(
    val progress: Map<String, String> = emptyMap(),
    val complete: Boolean = false,
    val success: Boolean = false
)
