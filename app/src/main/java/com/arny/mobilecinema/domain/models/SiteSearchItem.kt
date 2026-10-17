package com.arny.mobilecinema.domain.models

/**
 * Карточка из результатов поиска на сайте (страницы /films/search/ и /serials/search/).
 *
 * Это только предпросмотр: в БД карточка попадает позже, при открытии результата
 * (полный разбор страницы фильма/сериала).
 */
data class SiteSearchItem(
    val pageUrl: String = "",
    val title: String = "",
    val origTitle: String = "",
    val img: String = "",
    val year: Int = 0,
    val rating: Float = 0f,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val description: String = "",
    val type: MovieType = MovieType.NO_TYPE,
)
