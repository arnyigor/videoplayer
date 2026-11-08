package com.arny.mobilecinema.presentation.sitesearch

import com.arny.mobilecinema.domain.search.RatingSource
import com.arny.mobilecinema.domain.search.SearchHit
import java.util.Locale

/**
 * Подпись рейтинга в выдаче: «голое» число не говорит, что это за оценка.
 * Каталог хранит IMDb, у сайта источник свой — так и подписываем.
 */
fun SearchHit.ratingLabel(): String? {
    if (rating <= 0f) return null
    val value = String.format(Locale.getDefault(), "%.1f", rating)
    return when (ratingSource) {
        RatingSource.IMDB -> "IMDb $value"
        RatingSource.SITE -> "Рейтинг $value"
        RatingSource.NONE -> null
    }
}
