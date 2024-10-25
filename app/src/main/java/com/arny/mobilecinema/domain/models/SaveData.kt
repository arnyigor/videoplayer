package com.arny.mobilecinema.domain.models

data class SaveData(
    val movieDbId: Long? = null,
    val time: Long = 0,
    val seasonPosition: Int = 0,
    val episodePosition: Int = 0,
    val latestTime: Long = 0L,
)
