package com.arny.mobilecinema.presentation.playerview

import com.arny.mobilecinema.domain.models.Movie

data class PlayerUiState(
    val path: String? = null,
    val time: Long = 0,
    val movie: Movie? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val excludeUrls: Set<String> = emptySet(),
)