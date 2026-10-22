package com.arny.mobilecinema.presentation.home

import android.widget.ImageView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
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
    val request = Glide.with(this)
        .load(path.getWithDomain(baseUrl))
        .placeholder(R.drawable.placeholder_movie)
        .error(R.drawable.placeholder_movie)
        .transition(DrawableTransitionOptions.withCrossFade(200))
        .centerCrop()
    if (targetWidthPx > 0) {
        // Постер 2:3 — высоту просим пропорционально ширине.
        request.override(targetWidthPx, targetWidthPx * 3 / 2)
    }
    request.into(this)
}

fun ViewMovie.ratingLabel(): String? = when {
    ratingImdb > 0.0 -> String.format(Locale.getDefault(), "IMDb %.1f", ratingImdb)
    ratingKp > 0.0 -> String.format(Locale.getDefault(), "KP %.1f", ratingKp)
    else -> null
}
