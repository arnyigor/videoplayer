package com.arny.mobilecinema.presentation.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.catalog.CatalogSort
import com.arny.mobilecinema.presentation.catalog.CatalogUiState
import com.arny.mobilecinema.presentation.tv.ui.components.PosterCard
import com.arny.mobilecinema.presentation.tv.ui.components.SectionHeader
import com.arny.mobilecinema.presentation.tv.ui.components.TvEmptyState
import com.arny.mobilecinema.presentation.tv.ui.components.TvErrorState
import com.arny.mobilecinema.presentation.tv.ui.components.TvFilterChip
import com.arny.mobilecinema.presentation.tv.ui.components.TvLoadingState
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/** Сколько последних видимых ячеек остаётся до запроса следующей страницы. */
private const val PRELOAD_DISTANCE = 8

/**
 * Каталог «Фильмы»/«Сериалы» (ТЗ §4): заголовок, чипы сортировки, чипы жанров, сетка 2:3.
 * Фокус на чипе не меняет выдачу — применяется только OK.
 */
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onOpenTitle: (Long) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSelectSort: (CatalogSort) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    imageBaseUrl: String = "",
    gridState: LazyGridState = rememberLazyGridState(),
    restoreFocusTitleId: Long? = null,
    onTitleFocused: (Long) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(TvColors.Background),
    ) {
        SectionHeader(
            title = stringResource(state.section.titleRes),
            count = state.items.size.takeIf { it > 0 },
        )
        Row(
            modifier = Modifier.padding(horizontal = TvDimens.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
        ) {
            CatalogSort.entries.forEach { sort ->
                TvFilterChip(
                    label = stringResource(sort.titleRes),
                    selected = state.sort == sort,
                    onClick = { onSelectSort(sort) },
                )
            }
        }
        if (state.genres.isNotEmpty()) {
            GenreChips(
                genres = state.genres,
                selected = state.genre,
                onSelectGenre = onSelectGenre,
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> TvLoadingState()
                state.error && state.items.isEmpty() -> TvErrorState(
                    message = stringResource(R.string.tv_catalog_error),
                    onRetry = onRetry,
                )
                state.items.isEmpty() -> TvEmptyState(message = stringResource(R.string.tv_catalog_empty))
                else -> CatalogGrid(
                    state = state,
                    gridState = gridState,
                    onOpenTitle = onOpenTitle,
                    onLoadMore = onLoadMore,
                    restoreFocusTitleId = restoreFocusTitleId,
                    onTitleFocused = onTitleFocused,
                    imageBaseUrl = imageBaseUrl,
                )
            }
        }
    }
}

@Composable
private fun GenreChips(
    genres: List<String>,
    selected: String?,
    onSelectGenre: (String?) -> Unit,
) {
    val allLabel = stringResource(R.string.home_content_all)
    LazyRow(
        modifier = Modifier.padding(
            horizontal = TvDimens.ScreenHorizontal,
            vertical = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
    ) {
        item(key = "all") {
            TvFilterChip(
                label = allLabel,
                selected = selected == null,
                onClick = { onSelectGenre(null) },
            )
        }
        items(items = genres, key = { it }) { genre ->
            TvFilterChip(
                label = genre,
                selected = genre == selected,
                onClick = { onSelectGenre(genre) },
            )
        }
    }
}

@Composable
private fun CatalogGrid(
    state: CatalogUiState,
    gridState: LazyGridState,
    onOpenTitle: (Long) -> Unit,
    onLoadMore: () -> Unit,
    restoreFocusTitleId: Long?,
    onTitleFocused: (Long) -> Unit,
    imageBaseUrl: String,
) {
    val focusRequester = remember { FocusRequester() }
    var focusRestored by remember { mutableStateOf(false) }

    LaunchedEffect(restoreFocusTitleId, state.items.size) {
        val targetId = restoreFocusTitleId ?: return@LaunchedEffect
        if (focusRestored) return@LaunchedEffect
        val index = state.items.indexOfFirst { it.dbId == targetId }
        if (index < 0) return@LaunchedEffect
        gridState.scrollToItem(index)
        // Ждём кадр: карточка должна быть в composition, иначе requestFocus() не найдёт узел.
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        focusRestored = true
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - PRELOAD_DISTANCE) onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TvDimens.PosterWidth),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvDimens.ScreenHorizontal,
            end = TvDimens.ScreenHorizontal,
            top = 8.dp,
            bottom = TvDimens.SectionGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
        verticalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
    ) {
        items(items = state.items, key = { it.dbId }) { movie ->
            // Ячейка адаптивной сетки шире постера 124 dp — центрируем карточку, чтобы не было рваных отступов.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
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
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) onTitleFocused(movie.dbId)
                        },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CatalogScreenPreview() {
    val movies = listOf(
        ViewMovie(dbId = 1, title = "Проект «Конец света»", type = MovieType.CINEMA.value, year = 2026),
        ViewMovie(dbId = 2, title = "Обитель зла", type = MovieType.CINEMA.value, year = 2026),
        ViewMovie(dbId = 3, title = "Дюна: часть вторая", type = MovieType.CINEMA.value, year = 2024),
        ViewMovie(dbId = 4, title = "Оппенгеймер", type = MovieType.CINEMA.value, year = 2023),
    )
    TvTheme {
        CatalogScreen(
            state = CatalogUiState(
                section = CatalogSection.FILMS,
                genres = listOf("боевик", "драма", "фантастика"),
                genre = "фантастика",
                items = movies,
                isLoading = false,
            ),
            onOpenTitle = {},
            onSelectGenre = {},
            onSelectSort = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
