package com.arny.mobilecinema.presentation.catalog

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie

/**
 * Раздел каталога (ТЗ §4). Один экран обслуживает оба раздела: меняется только тип выдачи,
 * поэтому навигационный аргумент — строковый ключ, а не отдельный destination.
 */
enum class CatalogSection(
    val movieType: MovieType,
    val titleRes: Int,
) {
    FILMS(MovieType.CINEMA, R.string.home_content_cinema),
    SERIES(MovieType.SERIAL, R.string.home_content_serial),
    ;

    companion object {
        fun fromKey(key: String?): CatalogSection =
            entries.firstOrNull { it.name == key } ?: FILMS
    }
}

/**
 * Сортировка каталога. Порядок отдаёт существующий слой (`AppConstants.Order`),
 * поэтому список ограничен тремя вариантами, которые дают разные выдачи.
 */
enum class CatalogSort(
    val order: String,
    val titleRes: Int,
) {
    NEW(AppConstants.Order.YEAR_DESC, R.string.new_movies),
    RATING(AppConstants.Order.RATINGS, R.string.by_rating_movies),
    ALPHABET(AppConstants.Order.TITLE, R.string.alphabet_movies),
}

data class CatalogUiState(
    val section: CatalogSection = CatalogSection.FILMS,
    val sort: CatalogSort = CatalogSort.NEW,
    val genre: String? = null,
    val genres: List<String> = emptyList(),
    val items: List<ViewMovie> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: Boolean = false,
)

/**
 * Чистые переходы состояния каталога: без Android и без корутин, поэтому покрываются unit-тестами.
 * Каталог не использует Paging3: сетка сама сообщает о достижении конца и просит следующую страницу.
 */
object CatalogPaging {

    const val PAGE_SIZE = 24

    fun withNextPage(
        state: CatalogUiState,
        page: List<ViewMovie>,
        pageSize: Int = PAGE_SIZE,
    ): CatalogUiState = state.copy(
        items = (state.items + page).distinctBy { it.dbId },
        isLoading = false,
        isLoadingMore = false,
        error = false,
        // Короткая страница означает конец выдачи; дубликаты не должны приводить к вечной догрузке.
        endReached = page.size < pageSize,
    )

    fun withLoadingMore(state: CatalogUiState): CatalogUiState =
        state.copy(isLoadingMore = true, error = false)

    fun withError(state: CatalogUiState): CatalogUiState =
        state.copy(isLoading = false, isLoadingMore = false, error = true)

    /** Смена фильтра/сортировки сбрасывает накопленную выдачу и просит первую страницу заново. */
    fun withFilter(
        state: CatalogUiState,
        genre: String? = state.genre,
        sort: CatalogSort = state.sort,
    ): CatalogUiState = state.copy(
        genre = genre,
        sort = sort,
        items = emptyList(),
        isLoading = true,
        isLoadingMore = false,
        endReached = false,
        error = false,
    )

    fun withGenres(state: CatalogUiState, genres: List<String>): CatalogUiState =
        state.copy(genres = genres)
}
