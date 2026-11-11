package com.arny.mobilecinema.presentation.details

import androidx.annotation.StringRes
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.MovieType

/**
 * Итог удаления истории/кэша на экране деталей.
 *
 * Вынесено из ViewModel: ровно одно сообщение и не более одного выхода назад —
 * раньше повторная эмиссия приводила к «прыжку» на список истории и обратно на карточку.
 */
data class HistoryClearOutcome(
    @StringRes val messageRes: Int,
    val navigateBack: Boolean,
)

fun historyClearOutcome(movieType: MovieType?, total: Boolean): HistoryClearOutcome? = when (movieType) {
    MovieType.CINEMA -> if (total) {
        HistoryClearOutcome(R.string.movie_viewhistory_cleared, navigateBack = true)
    } else {
        HistoryClearOutcome(R.string.movie_cache_cleared, navigateBack = false)
    }

    MovieType.SERIAL -> if (total) {
        HistoryClearOutcome(R.string.serial_history_cleared, navigateBack = true)
    } else {
        HistoryClearOutcome(R.string.serial_episode_cache_cleared, navigateBack = false)
    }

    else -> null
}
