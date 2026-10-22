package com.arny.mobilecinema.presentation.mobile.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.catalog.CatalogSort
import com.arny.mobilecinema.presentation.catalog.CatalogUiState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileEmptyState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileErrorState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobilePosterCard
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileColors
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme

/** Сколько последних видимых ячеек остаётся до запроса следующей страницы. */
private const val PRELOAD_DISTANCE = 6

/**
 * Каталог «Фильмы»/«Сериалы» на телефоне (CAT-01 для мобильного трека).
 *
 * Сетка адаптивная: число колонок считает `PosterGrid` по ширине окна, поэтому на планшете
 * карточки не растягиваются. Состояние прокрутки переживает переход в другую вкладку —
 * `LazyGridState` хранится во ViewModel.
 *
 * Сортировка — компактный селектор в строке заголовка: три chip-а занимали целую полосу,
 * а жанрам оставалось меньше места (аудит §3.2, UI-03).
 */
@Composable
fun MobileCatalogScreen(
    state: CatalogUiState,
    onOpenTitle: (Long) -> Unit,
    onSelectSort: (CatalogSort) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    imageBaseUrl: String = "",
    gridState: LazyGridState = rememberLazyGridState(),
    columns: Int = 2,
    /** Заголовок раздела нужен только когда экран — самостоятельная вкладка. */
    showTitle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        top = MobileDimens.RowGap,
        bottom = MobileDimens.SectionGap,
    ),
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MobileColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MobileDimens.ScreenPadding,
                    end = MobileDimens.ScreenPadding,
                    top = MobileDimens.ScreenPadding,
                    bottom = MobileDimens.RowGap,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.End,
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(state.section.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MobileColors.OnBackground,
                )
            }
            CatalogSortSelector(
                sort = state.sort,
                onSelectSort = onSelectSort,
            )
        }

        if (state.genres.isNotEmpty()) {
            CatalogGenreChips(
                genres = state.genres,
                selected = state.genre,
                onSelectGenre = onSelectGenre,
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                // Skeleton вместо спиннера: геометрия сетки не прыгает при приезде данных.
                state.isLoading -> CatalogSkeleton(columns = columns)

                state.error && state.items.isEmpty() -> MobileErrorState(
                    message = stringResource(R.string.tv_catalog_error),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.items.isEmpty() -> MobileEmptyState(
                    message = stringResource(R.string.tv_catalog_empty),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> CatalogGrid(
                    state = state,
                    gridState = gridState,
                    columns = columns,
                    imageBaseUrl = imageBaseUrl,
                    contentPadding = contentPadding,
                    onOpenTitle = onOpenTitle,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

/**
 * Сортировка в тулбаре каталога: «Сортировка: Новинки ▾» открывает меню.
 * Три chip-а в ряд отнимали полосу у жанров (аудит §3.2).
 */
@Composable
private fun CatalogSortSelector(
    sort: CatalogSort,
    onSelectSort: (CatalogSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortTitle = stringResource(sort.titleRes)
    val selectorLabel = stringResource(R.string.catalog_sort_selector, sortTitle)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(MobileDimens.MinTouchTarget)
                .semantics { contentDescription = selectorLabel },
        ) {
            Text(
                text = "$selectorLabel ▾",
                style = MaterialTheme.typography.labelLarge,
                color = MobileColors.Accent,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MobileColors.SurfaceElevated),
        ) {
            CatalogSort.entries.forEach { option ->
                val title = stringResource(option.titleRes)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = title,
                            color = if (option == sort) MobileColors.Accent else MobileColors.OnBackground,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectSort(option)
                    },
                )
            }
        }
    }
}

/**
 * Жанры — один горизонтальный ряд. Выбранный жанр подкручивается в видимую область:
 * раньше активный chip мог остаться обрезанным на краю экрана (аудит §3.4).
 */
@Composable
private fun CatalogGenreChips(
    genres: List<String>,
    selected: String?,
    onSelectGenre: (String?) -> Unit,
) {
    val listState = rememberLazyListState()
    // Первый элемент — «Все» (значение null), дальше жанры по порядку.
    val selectedIndex = remember(genres, selected) {
        if (selected == null) 0 else genres.indexOf(selected) + 1
    }
    LaunchedEffect(selectedIndex, listState) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        // padding + clip: прокрученный ряд не вылезает на системный край обрезанным
        // краем соседнего чипа (аудит §4). Сам ряд клипается по границам отступа.
        modifier = Modifier.padding(horizontal = MobileDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.RowGap),
    ) {
        item(key = "all") {
            CatalogChip(
                label = stringResource(R.string.home_content_all),
                selected = selected == null,
                onClick = { onSelectGenre(null) },
            )
        }
        itemsIndexed(genres, key = { _, genre -> genre }) { _, genre ->
            CatalogChip(
                label = genre,
                selected = selected == genre,
                onClick = { onSelectGenre(genre) },
            )
        }
    }
}

/**
 * Невыбранный фильтр должен читаться, а не выглядеть выключенным: раньше он рисовался
 * серым на сером (аудит §3.1, UI-03).
 */
@Composable
private fun CatalogChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(MobileDimens.ChipRadius),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MobileColors.Surface,
            labelColor = MobileColors.OnBackground,
            selectedContainerColor = MobileColors.Accent,
            selectedLabelColor = MobileColors.OnAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MobileColors.Outline,
            selectedBorderColor = MobileColors.Accent,
        ),
    )
}

@Composable
private fun CatalogGrid(
    state: CatalogUiState,
    gridState: LazyGridState,
    columns: Int,
    imageBaseUrl: String,
    contentPadding: PaddingValues,
    onOpenTitle: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(gridState, state.items.size, state.endReached, state.error) {
        if (state.endReached || state.error) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - PRELOAD_DISTANCE) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
    ) {
        items(state.items, key = { it.dbId }) { movie ->
            MobilePosterCard(
                title = movie.title,
                posterUrl = movie.posterUrl(imageBaseUrl),
                modifier = Modifier.fillMaxWidth(),
                subtitle = movie.year.takeIf { it > 0 }?.toString(),
                onClick = { onOpenTitle(movie.dbId) },
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MobileColors.Accent)
                }
            }
        }
    }
}

/** Скелетон первой страницы: та же сетка, что и у контента, поэтому нет скачка вёрстки. */
@Composable
private fun CatalogSkeleton(columns: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        contentPadding = PaddingValues(
            bottom = MobileDimens.SectionGap,
            top = MobileDimens.RowGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
        userScrollEnabled = false,
    ) {
        items(SKELETON_CELLS, key = { it }) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MobileDimens.PosterHeight)
                        .background(MobileColors.Surface, RoundedCornerShape(MobileDimens.CornerRadius)),
                )
                Box(
                    Modifier
                        .padding(top = MobileDimens.RowGap)
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .background(MobileColors.Surface),
                )
            }
        }
    }
}

private val SKELETON_CELLS = List(6) { it }

private fun ViewMovie.posterUrl(baseUrl: String): String =
    if (img.isBlank()) "" else img.getWithDomain(baseUrl)

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
private fun MobileCatalogPreview() {
    MobileTheme(darkTheme = true) {
        MobileCatalogScreen(
            state = CatalogUiState(
                section = CatalogSection.FILMS,
                genres = listOf("боевик", "драма"),
                isLoading = false,
                items = listOf(
                    ViewMovie(dbId = 1, title = "Проект «Конец света»", year = 2026),
                    ViewMovie(dbId = 2, title = "Рыцарь Семи Королевств", year = 2026),
                ),
            ),
            onOpenTitle = {},
            onSelectSort = {},
            onSelectGenre = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
