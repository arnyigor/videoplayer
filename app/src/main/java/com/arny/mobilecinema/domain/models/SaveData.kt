package com.arny.mobilecinema.domain.models

data class SaveData(
    val movieDbId: Long? = null,
    val position: Long = 0,
    val seasonPosition: Int = 0,
    val episodePosition: Int = 0
)
