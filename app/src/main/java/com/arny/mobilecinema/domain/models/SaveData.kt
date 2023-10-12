package com.arny.mobilecinema.domain.models

data class SaveData(
    val movieDbId: Long? = null,
    val position: Long = 0,
    val season: Int = 0,
    val episode: Int = 0
)
