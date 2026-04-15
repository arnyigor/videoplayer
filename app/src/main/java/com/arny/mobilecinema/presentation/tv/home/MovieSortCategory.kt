package com.arny.mobilecinema.presentation.tv.home

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants

enum class MovieSortCategory(val labelResId: Int, val order: String) {
    NEW(R.string.new_movies, AppConstants.Order.YEAR_DESC),
    POPULAR(R.string.popular_movies, AppConstants.Order.RATINGS),
    ALPHABET(R.string.alphabet_movies, AppConstants.Order.TITLE),
    RATING(R.string.by_rating_movies, AppConstants.Order.RATINGS)
}