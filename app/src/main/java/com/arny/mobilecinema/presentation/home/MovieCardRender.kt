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

fun ImageView.loadMoviePoster(path: String, baseUrl: String) {
    Glide.with(this)
        .load(path.getWithDomain(baseUrl))
        .placeholder(R.drawable.placeholder_movie)
        .error(R.drawable.placeholder_movie)
        .transition(DrawableTransitionOptions.withCrossFade(200))
        .centerCrop()
        .into(this)
}

fun ViewMovie.ratingLabel(): String? = when {
    ratingImdb > 0.0 -> String.format(Locale.getDefault(), "IMDb %.1f", ratingImdb)
    ratingKp > 0.0 -> String.format(Locale.getDefault(), "KP %.1f", ratingKp)
    else -> null
}
