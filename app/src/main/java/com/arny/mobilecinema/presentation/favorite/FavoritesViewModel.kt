package com.arny.mobilecinema.presentation.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.home.UiAction
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана «Избранное».
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FavoritesViewModel @AssistedInject constructor(
    private val interactor: MoviesInteractor,
) : ViewModel() {

    /* ---------- UI‑состояния ---------- */
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _empty = MutableStateFlow(true)
    val empty = _empty.asStateFlow()

    private val _order = MutableStateFlow("")
    val order = _order.asStateFlow()

    /* ---------- Логика поиска ---------- */
    private var search = UiAction.Search()
    private var started = false
    private var query = ""
    private var searchType = ""

    private val actionStateFlow = MutableSharedFlow<UiAction>()

    /** Пайплайн получения страниц избранных фильмов. */
    val favoriteDataFlow: Flow<PagingData<ViewMovie>> =
        actionStateFlow
            .filterIsInstance<UiAction.Search>()
            .distinctUntilChanged()
            .debounce(350)
            .onStart {
                started = true

                // Получаем сохранённый порядок сортировки для избранного.
                val savedOrder = interactor.getOrder(true)   // при необходимости можно изменить метод
                _order.value = savedOrder

                emit(
                    UiAction.Search(
                        order = savedOrder,
                        searchType = searchType
                    )
                )
            }
            .flatMapLatest { search ->
                this.search = search
                interactor.getFavoriteMovies(
                    search = search.query,
                    order = search.order,
                    searchType = search.searchType
                )
            }
            .onEach {
                _loading.value = false
                checkEmpty()
            }
            .cachedIn(viewModelScope)

    fun loadFavorites(query: String = "", submit: Boolean = true, delay: Boolean = false) {
        viewModelScope.launch {
            this@FavoritesViewModel.query = query
            if (submit) {
                if (delay) delay(350)
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = query,
                        order = _order.value
                    )
                )
            }
        }
    }

    fun setOrder(order: String) {
        viewModelScope.launch {
            interactor.saveFavoriteOrder(order)
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = search.query,
                    order = order
                )
            )
        }
    }

    fun setSearchType(type: String, submit: Boolean = true) {
        viewModelScope.launch {
            searchType = type
            if (submit) {
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = search.query,
                        order = _order.value
                    )
                )
            }
        }
    }

    private fun checkEmpty() {
        viewModelScope.launch {
            interactor.isFavoriteEmpty()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> _empty.value = data.result
                    }
                }
        }
    }

    fun clearAllFavoriteHistory() {
        viewModelScope.launch {
            interactor.clearAllFavorites()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> {
                            reloadFavorites()
                        }
                    }
                }
        }
    }

    fun reloadFavorites() {
        if (started) {
            viewModelScope.launch {
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = query,
                        order = _order.value,
                        triggerId = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
