package com.arny.mobilecinema.presentation.sitesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SiteSearchUiState(
    val query: String = "",
    val items: List<SiteSearchItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOpening: Boolean = false,
    val searched: Boolean = false,
    val filmsPage: Int = 0,
    val hasMoreFilms: Boolean = false,
)

/**
 * Поиск по каталогу сайта. Нужен для карточек, которых нет в локальной базе
 * (например, если обход листингов до них ещё не дошёл).
 */
class SiteSearchViewModel(
    private val interactor: JsoupUpdateInteractor,
) : ViewModel() {

    private companion object {
        /** Столько карточек фильмов сайт отдаёт на страницу результатов. */
        const val FILMS_PAGE_SIZE = 10
    }

    private val _uiState = MutableStateFlow(SiteSearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _toast = BufferedChannel<IWrappedString>()
    val toast = _toast.receiveAsFlow()

    private val _openMovie = BufferedChannel<Long>()
    val openMovie = _openMovie.receiveAsFlow()

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank() || _uiState.value.isLoading) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    searched = true,
                    items = emptyList(),
                    filmsPage = 0,
                    hasMoreFilms = false,
                )
            }
            when (val result = interactor.searchOnSite(query = query, page = 1)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = result.result,
                        filmsPage = 1,
                        hasMoreFilms = result.result.count { item ->
                            item.type == MovieType.CINEMA
                        } >= FILMS_PAGE_SIZE,
                    )
                }

                is DataResult.Error -> {
                    Timber.e(result.throwable, "site search failed")
                    _uiState.update { it.copy(isLoading = false) }
                    _toast.trySend(ResourceString(R.string.site_search_error))
                }
            }
        }
    }

    /** Следующая страница поиска фильмов (у поиска сериалов пагинации нет). */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMoreFilms || state.isLoading || state.isLoadingMore) {
            return
        }
        val query = state.query.trim()
        if (query.isBlank()) {
            return
        }
        viewModelScope.launch {
            val nextPage = state.filmsPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }
            when (val result = interactor.searchOnSite(query = query, page = nextPage)) {
                is DataResult.Success -> {
                    val films = result.result.filter { item -> item.type == MovieType.CINEMA }
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMore = false,
                            filmsPage = nextPage,
                            items = (current.items + films).distinctBy { it.pageUrl },
                            hasMoreFilms = films.size >= FILMS_PAGE_SIZE,
                        )
                    }
                }

                is DataResult.Error -> {
                    Timber.e(result.throwable, "site search page $nextPage failed")
                    _uiState.update { it.copy(isLoadingMore = false, hasMoreFilms = false) }
                    _toast.trySend(ResourceString(R.string.site_search_error))
                }
            }
        }
    }

    /** Карточка сохраняется (или уже есть в БД), детали и серии дозагрузит экран деталей. */
    fun openItem(item: SiteSearchItem) {
        if (_uiState.value.isOpening) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true) }
            when (val result = interactor.fetchAndSaveMovieCard(item.pageUrl)) {
                is DataResult.Success -> {
                    _uiState.update { it.copy(isOpening = false) }
                    _openMovie.trySend(result.result)
                }

                is DataResult.Error -> {
                    Timber.e(result.throwable, "site search open ${item.pageUrl} failed")
                    _uiState.update { it.copy(isOpening = false) }
                    _toast.trySend(ResourceString(R.string.site_search_open_error))
                }
            }
        }
    }
}
