package com.arny.mobilecinema.domain.models

data class DataUpdateResult(
    val updateDateTime: String,
    val hasPartUpdate: Boolean = false
)
