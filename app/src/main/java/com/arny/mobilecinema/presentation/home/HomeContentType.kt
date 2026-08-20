package com.arny.mobilecinema.presentation.home

import com.arny.mobilecinema.data.repository.AppConstants

enum class HomeContentType(val searchTypes: List<String>) {
    ALL(
        listOf(
            AppConstants.SearchType.CINEMA,
            AppConstants.SearchType.SERIAL
        )
    ),
    CINEMA(listOf(AppConstants.SearchType.CINEMA)),
    SERIAL(listOf(AppConstants.SearchType.SERIAL))
}
