package com.arny.mobilecinema.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Состояние каталога «Фильмы»/«Сериалы» (CAT-01…03), общее для TV и телефона.
 *
 * Позиция сетки и фокус хранятся во ViewModel, а не в composition: view фрагмента уничтожается
 * при переходе в детали, и возврат обязан восстановить и скролл, и карточку (ТЗ §9.2).
 */
class CatalogViewModel(
    private val moviesInteractor: MoviesInteractor,
    sectionKey: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CatalogUiState(section = CatalogSection.fromKey(sectionKey))
    )
    val uiState = _uiState.asStateFlow()

    var gridFirstIndex: Int = 0
        private set
    var gridScrollOffset: Int = 0
        private set

    var focusedTitleId: Long? = null
        private set

    private var pageJob: Job? = null

    init {
        loadGenres()
        reload()
    }

    fun saveGridScroll(index: Int, offset: Int) {
        gridFirstIndex = index
        gridScrollOffset = offset
    }

    fun saveFocusedTitle(titleId: Long?) {
        focusedTitleId = titleId
    }

    fun selectGenre(genre: String?) {
        if (_uiState.value.genre == genre) return
        _uiState.update { CatalogPaging.withFilter(it, genre = genre) }
        reload()
    }

    fun selectSort(sort: CatalogSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { CatalogPaging.withFilter(it, sort = sort) }
        reload()
    }

    fun retry() {
        _uiState.update { it.copy(items = emptyList(), isLoading = true, error = false, endReached = false) }
        reload()
    }

    /** Сетка сообщает о подходе к концу списка; повторные вызовы отсекаются флагами состояния. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached || state.error) return
        _uiState.update(CatalogPaging::withLoadingMore)
        loadPage(reset = false)
    }

    private fun reload() {
        pageJob?.cancel()
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        val state = _uiState.value
        val offset = if (reset) 0 else state.items.size
        pageJob = viewModelScope.launch {
            try {
                val page = moviesInteractor.getCatalogPage(
                    movieType = state.section.movieType,
                    order = state.sort.order,
                    genres = listOfNotNull(state.genre),
                    limit = CatalogPaging.PAGE_SIZE,
                    offset = offset,
                )
                _uiState.update { CatalogPaging.withNextPage(it, page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load catalog page (offset=$offset)")
                _uiState.update(CatalogPaging::withError)
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            val genres = runCatching { moviesInteractor.loadCanonicalGenres() }
                .onFailure { Timber.e(it, "Failed to load catalog genres") }
                .getOrDefault(emptyList())
            _uiState.update { CatalogPaging.withGenres(it, genres) }
        }
    }
}
