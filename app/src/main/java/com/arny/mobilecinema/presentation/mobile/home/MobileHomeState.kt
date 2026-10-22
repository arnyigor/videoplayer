package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.domain.models.HomeHighlights

/** Что показывает главная: скелетон, ошибку, пустую ленту или контент (ТЗ ч. II §20.3). */
enum class MobileHomeMode { LOADING, ERROR, EMPTY, CONTENT }

/**
 * Состояние Compose-главной (MOB-09C).
 *
 * Блоки не хранятся: они выводятся из данных фабрикой `MobileHomeRowsFactory`, поэтому тик
 * прогресса обновляет только `continueCards` и не перезагружает остальные ряды.
 */
data class MobileHomeUiState(
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val highlights: HomeHighlights = HomeHighlights(),
    val continueCards: List<ContinueCardModel> = emptyList(),
    val baseUrl: String = "",
) {
    val content: MobileHomeContent
        get() = MobileHomeRowsFactory.build(highlights, continueCards)

    val mode: MobileHomeMode
        get() = when {
            isLoading -> MobileHomeMode.LOADING
            error && content.blocks.isEmpty() && content.featured == null -> MobileHomeMode.ERROR
            content.blocks.isEmpty() && content.featured == null -> MobileHomeMode.EMPTY
            else -> MobileHomeMode.CONTENT
        }
}
