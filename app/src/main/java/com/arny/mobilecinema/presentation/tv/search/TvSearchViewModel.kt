package com.arny.mobilecinema.presentation.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchFilter(val labelResId: Int, val searchTypes: List<String>) {
    ALL(R.string.all_types, listOf(AppConstants.SearchType.CINEMA, AppConstants.SearchType.SERIAL)),
    CINEMA_ONLY(R.string.cinema_only, listOf(AppConstants.SearchType.CINEMA)),
    SERIAL_ONLY(R.string.serials_only, listOf(AppConstants.SearchType.SERIAL))
}

@OptIn(ExperimentalCoroutinesApi::class)
class TvSearchViewModel(
    private val moviesInteractor: MoviesInteractor
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _searchType = MutableStateFlow(AppConstants.SearchType.TITLE)
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter = _filter.asStateFlow()

    @OptIn(FlowPreview::class)
    val searchResults: Flow<PagingData<ViewMovie>> = combine(
        _searchQuery.debounce(300).distinctUntilChanged(),
        _searchType,
        _filter
    ) { query, searchType, filterOption ->
        Triple(query, searchType, filterOption)
    }.flatMapLatest { (query, searchType, filterOption) ->
        if (query.isBlank()) {
            flowOf(PagingData.empty())
        } else {
            moviesInteractor.getMovies(
                search = query,
                order = AppConstants.Order.NONE,
                searchType = searchType,
                searchAddTypes = filterOption.searchTypes
            )
        }
    }.cachedIn(viewModelScope)

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setSearchType(searchType: String) {
        _searchType.value = searchType.ifBlank { AppConstants.SearchType.TITLE }
    }

    fun setFilter(filter: SearchFilter) {
        _filter.value = filter
        // Перезапускаем поиск с новым фильтром
        val currentQuery = _searchQuery.value
        if (currentQuery.isNotBlank()) {
            _searchQuery.value = currentQuery
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}
