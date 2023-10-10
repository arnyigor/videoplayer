package com.arny.mobilecinema.presentation.extendedsearch

import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange

data class ExtendSearchResult(
    val search: String = "",
    val types: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val yearsRange: SimpleIntRange = SimpleIntRange(0, 0),
    val imdbRange: SimpleFloatRange = SimpleFloatRange(0.0f, 0.0f),
    val kpRange: SimpleFloatRange = SimpleFloatRange(0.0f, 0.0f),
)
