package com.arny.mobilecinema.presentation.utils

import android.content.Context

/**
 * Расчёт сетки постеров для телефона и планшета (MOB-07).
 *
 * На телефоне 360 dp получается 2 колонки (как и было), на планшете 800 dp — 4,
 * на большом окне — до 6. Ограничение сверху нужно, чтобы карточка не уменьшалась
 * до нечитаемого состояния: 6 колонок на 1280 dp дают ~213 dp на карточку.
 */
object PosterGrid {

    /** Минимальная ширина карточки по ТЗ §23: меньше — название не читается. */
    const val MIN_CELL_WIDTH_DP = 180
    const val MIN_SPANS = 2
    const val MAX_SPANS = 6

    fun spansFor(widthDp: Int): Int =
        if (widthDp <= 0) MIN_SPANS else (widthDp / MIN_CELL_WIDTH_DP).coerceIn(MIN_SPANS, MAX_SPANS)

    /** Ширина окна в dp за вычетом горизонтальных отступов списка. */
    fun windowWidthDp(context: Context, horizontalPaddingDp: Int = 0): Int =
        context.resources.configuration.screenWidthDp - horizontalPaddingDp

    /** Ширина одного постера в пикселях — под неё просим картинку у Glide. */
    fun posterWidthPx(widthDp: Int, spans: Int, density: Float): Int {
        val safeSpans = spans.coerceAtLeast(1)
        return ((widthDp.coerceAtLeast(0) / safeSpans) * density).toInt().coerceAtLeast(1)
    }
}
