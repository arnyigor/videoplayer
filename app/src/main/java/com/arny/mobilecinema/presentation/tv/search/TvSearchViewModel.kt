package com.arny.mobilecinema.presentation.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvSearchViewModel(
    private val moviesInteractor: MoviesInteractor
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    
    @OptIn(FlowPreview::class)
    val searchResults: Flow<PagingData<ViewMovie>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                moviesInteractor.getMovies(
                    search = query,
                    order = AppConstants.Order.NONE,
                    searchType = "",
                    searchAddTypes = listOf(
                        AppConstants.SearchType.CINEMA,
                        AppConstants.SearchType.SERIAL
                    )
                )
            }
        }
        .cachedIn(viewModelScope)

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}
