package com.arny.mobilecinema.presentation.tv.home

import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.ContinueWatchingItem

enum class HomeRowType {
    CONTINUE,
    FOR_YOU,
    NEW,
    BEST_NOW,
    SERIES,
}

data class HomeRow(
    val type: HomeRowType,
    val id: String,
    val movies: List<ViewMovie> = emptyList(),
    val continueItems: List<ContinueWatchingItem> = emptyList(),
)

data class HomeContent(
    val hero: ViewMovie?,
    val rows: List<HomeRow>,
)

/**
 * Порядок блоков главной по ТЗ §3.1.
 *
 * Пустые блоки не создаются: новый профиль не должен видеть пустой «Продолжить»
 * или фиктивный «Для вас». Системные действия (обновление, очистка истории)
 * в этот список не входят — их место в настройках/rail.
 */
object HomeRowsFactory {

    fun build(
        highlights: HomeHighlights,
        continueWatching: List<ContinueWatchingItem>,
    ): HomeContent {
        val rows = buildList {
            if (continueWatching.isNotEmpty()) {
                add(
                    HomeRow(
                        type = HomeRowType.CONTINUE,
                        id = "continue",
                        continueItems = continueWatching,
                    )
                )
            }
            addPosterRow(HomeRowType.FOR_YOU, "for_you", highlights.forYou)
            addPosterRow(HomeRowType.NEW, "new", highlights.recent)
            addPosterRow(HomeRowType.BEST_NOW, "best_now", highlights.bestNow)

            val series = (highlights.recent + highlights.bestNow)
                .filter { it.type == MovieType.SERIAL.value }
                .distinctBy { it.dbId }
            addPosterRow(HomeRowType.SERIES, "series", series)
        }

        val hero = highlights.recent.firstOrNull()
            ?: highlights.bestNow.firstOrNull()
            ?: highlights.forYou.firstOrNull()

        return HomeContent(hero = hero, rows = rows)
    }

    private fun MutableList<HomeRow>.addPosterRow(
        type: HomeRowType,
        id: String,
        movies: List<ViewMovie>,
    ) {
        val distinct = movies.distinctBy { it.dbId }
        if (distinct.isNotEmpty()) {
            add(HomeRow(type = type, id = id, movies = distinct))
        }
    }
}
