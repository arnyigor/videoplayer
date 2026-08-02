package com.arny.mobilecinema.domain.models

data class MovieCommentsPage(
    val comments: List<MovieComment> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val addCommentUrl: String = "",
)
