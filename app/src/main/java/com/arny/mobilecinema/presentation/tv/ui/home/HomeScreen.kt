package com.arny.mobilecinema.presentation.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.ContinueWatchingItem
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.tv.home.HomeContent
import com.arny.mobilecinema.presentation.tv.home.HomeRowType
import com.arny.mobilecinema.presentation.tv.ui.components.ContinueWatchingCard
import com.arny.mobilecinema.presentation.tv.ui.components.PosterCard
import com.arny.mobilecinema.presentation.tv.ui.components.SectionHeader
import com.arny.mobilecinema.presentation.tv.ui.components.TvFilterChip
import com.arny.mobilecinema.presentation.tv.ui.components.TvHero
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/**
 * Главная на Compose: hero + ряды по порядку `HomeRowsFactory`.
 * Системные действия сюда не входят — они живут в rail/настройках.
 */
@Composable
fun HomeScreen(
    content: HomeContent,
    onOpenTitle: (Long) -> Unit,
    onContinue: (ContinueWatchingItem) -> Unit,
    /** Запуск воспроизведения с hero: плеер сам подставит сохранённую серию/позицию. */
    onPlay: (Long) -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenCatalog: (CatalogSection) -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    restoreFocusTitleId: Long? = null,
    onTitleFocused: (Long) -> Unit = {},
    imageBaseUrl: String = "",
) {
    val focusRequester = remember { FocusRequester() }
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(restoreFocusTitleId, content.rows) {
        if (!focusRestored && restoreFocusTitleId != null) {
            runCatching { focusRequester.requestFocus() }
            focusRestored = true
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvDimens.SectionGap),
    ) {
        val hero = content.hero
        if (hero != null) {
            item(key = "hero") {
                TvHero(
                    title = hero.title,
                    meta = heroMeta(hero),
                    description = "",
                    imageUrl = hero.img.takeIf { it.isNotBlank() }?.getWithDomain(imageBaseUrl),
                    primaryLabel = stringResource(R.string.play),
                    onPrimary = { onPlay(hero.dbId) },
                    secondaryLabel = stringResource(R.string.home_more),
                    onSecondary = { onOpenTitle(hero.dbId) },
                )
            }
        }

        // Временные входы в разделы до rail (CMP-07): они не зависят от наличия рядов.
        item(key = "sections") {
            Row(
                modifier = Modifier.padding(
                    horizontal = TvDimens.ScreenHorizontal,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
            ) {
                TvFilterChip(
                    label = stringResource(CatalogSection.FILMS.titleRes),
                    selected = false,
                    onClick = { onOpenCatalog(CatalogSection.FILMS) },
                )
                TvFilterChip(
                    label = stringResource(CatalogSection.SERIES.titleRes),
                    selected = false,
                    onClick = { onOpenCatalog(CatalogSection.SERIES) },
                )
                TvFilterChip(
                    label = stringResource(R.string.home_search),
                    selected = false,
                    onClick = onSearch,
                )
            }
        }

        items(items = content.rows, key = { it.id }) { row ->
            Column {
                SectionHeader(
                    title = rowTitle(row.type),
                    count = row.movies.size.takeIf { it > 0 },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = TvDimens.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
                ) {
                    if (row.continueItems.isNotEmpty()) {
                        items(items = row.continueItems, key = { it.playableId }) { item ->
                            ContinueWatchingCard(
                                title = item.title,
                                episodeLabel = item.episodeLabel,
                                imageUrl = item.posterUrl.takeIf { it.isNotBlank() }?.getWithDomain(imageBaseUrl),
                                progressFraction = item.progressFraction,
                                onClick = { onContinue(item) },
                            )
                        }
                    } else {
                        items(items = row.movies, key = { it.dbId }) { movie ->
                            PosterCard(
                                title = movie.title,
                                posterUrl = movie.img.takeIf { it.isNotBlank() }?.getWithDomain(imageBaseUrl),
                                onClick = { onOpenTitle(movie.dbId) },
                                modifier = Modifier
                                    .then(
                                        if (movie.dbId == restoreFocusTitleId) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onFocusChanged { state ->
                                        if (state.isFocused) onTitleFocused(movie.dbId)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rowTitle(type: HomeRowType): String = when (type) {
    HomeRowType.CONTINUE -> stringResource(R.string.home_continue)
    HomeRowType.FOR_YOU -> stringResource(R.string.home_for_you)
    HomeRowType.NEW -> stringResource(R.string.home_new)
    HomeRowType.BEST_NOW -> stringResource(R.string.home_best_now)
    HomeRowType.SERIES -> stringResource(R.string.home_series)
}

private fun heroMeta(movie: ViewMovie): String = buildList {
    if (movie.year > 0) add(movie.year.toString())
    if (movie.type == MovieType.SERIAL.value) add("сериал")
}.joinToString(" · ")

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val movies = listOf(
        ViewMovie(dbId = 1, title = "Проект «Конец света»", type = MovieType.CINEMA.value, year = 2026),
        ViewMovie(dbId = 2, title = "Обитель зла", type = MovieType.CINEMA.value, year = 2026),
        ViewMovie(dbId = 3, title = "Сериал", type = MovieType.SERIAL.value, year = 2025),
    )
    TvTheme {
        HomeScreen(
            content = HomeContent(
                hero = movies.first(),
                rows = listOf(
                    com.arny.mobilecinema.presentation.tv.home.HomeRow(
                        type = HomeRowType.NEW,
                        id = "new",
                        movies = movies,
                    ),
                    com.arny.mobilecinema.presentation.tv.home.HomeRow(
                        type = HomeRowType.CONTINUE,
                        id = "continue",
                        continueItems = listOf(
                            ContinueWatchingItem(
                                movieDbId = 3,
                                title = "Сериал",
                                posterUrl = "",
                                playableId = "ep:3:2:5",
                                positionMs = 1_160_000,
                                durationMs = 2_880_000,
                                seasonNumber = 2,
                                episodeNumber = 5,
                                updatedAt = 1,
                            )
                        ),
                    ),
                ),
            ),
            onOpenTitle = {},
            onContinue = {},
        )
    }
}
