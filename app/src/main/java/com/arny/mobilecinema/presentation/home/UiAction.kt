package com.arny.mobilecinema.presentation.home

sealed class UiAction {
    data class Search(
        val query: String = "",
        val order: String = "",
        val searchType: String = "",
        val searchAddTypes: List<String> = emptyList()
    ) : UiAction()
}