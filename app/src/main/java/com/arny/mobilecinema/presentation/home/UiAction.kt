package com.arny.mobilecinema.presentation.home

import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange

sealed class UiAction {
    data class Search(
        val query: String = "",
        val order: String = "",
        val searchType: String = "",
        val searchAddTypes: List<String> = emptyList(),
        val genres: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
        val years: SimpleIntRange? = null,
        val imdbs: SimpleFloatRange? = null,
        val kps: SimpleFloatRange? = null,
        val likesPriority: Boolean = false,
    ) : UiAction()
}