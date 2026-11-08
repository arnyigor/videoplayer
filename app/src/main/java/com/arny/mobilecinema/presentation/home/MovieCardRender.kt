package com.arny.mobilecinema.presentation.home

import android.widget.ImageView
import com.arny.mobilecinema.presentation.utils.loadPoster
import com.arny.mobilecinema.domain.models.ViewMovie
import java.util.Locale

/**
 * Общий рендер карточки фильма для домашнего экрана:
 * загрузка постера и подпись рейтинга. Используется горизонтальными рядами
 * подборок ([HomeHighlightMoviesAdapter]) и paging-сеткой ([VideoItemsAdapter]).
 */

/**
 * @param targetWidthPx ширина карточки в пикселях: без явного размера Glide декодирует
 * картинку под размер вьюхи и на планшете тянет лишние мегабайты (MOB-07).
 */
fun ImageView.loadMoviePoster(path: String, baseUrl: String, targetWidthPx: Int = 0) {
    loadPoster(path, baseUrl, targetWidthPx, targetWidthPx * 3 / 2)
}

fun ViewMovie.ratingLabel(): String? = when {
    ratingImdb > 0.0 -> String.format(Locale.getDefault(), "IMDb %.1f", ratingImdb)
    ratingKp > 0.0 -> String.format(Locale.getDefault(), "KP %.1f", ratingKp)
    else -> null
}
