package com.arny.mobilecinema.presentation.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModel constructor(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable = _updateAvailable.asStateFlow()

    val moviesDataFlow: Flow<PagingData<ViewMovie>> = flowOf(Unit)
        .flatMapLatest {
            moviesInteractor.getMovies(
                search = "",
                order = AppConstants.Order.NONE,
                searchType = "",
                searchAddTypes = listOf(
                    AppConstants.SearchType.CINEMA,
                    AppConstants.SearchType.SERIAL
                )
            )
        }
        .cachedIn(viewModelScope)

    private val _historyMovies = MutableStateFlow<List<ViewMovie>>(emptyList())
    val historyMovies: Flow<List<ViewMovie>> = _historyMovies.asStateFlow()

    private val _favoriteMovies = MutableStateFlow<List<ViewMovie>>(emptyList())
    val favoriteMovies: Flow<List<ViewMovie>> = _favoriteMovies.asStateFlow()

    init {
        loadFavorites()
        checkForUpdate()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            moviesInteractor.getFavoriteMovies(
                search = "",
                order = "",
                searchType = ""
            ).collectLatest { pagingData ->
                // For now, we'll just leave the list empty
                _favoriteMovies.value = emptyList()
            }
        }
    }

    fun onMovieSelected(movie: ViewMovie) {
        // Could show preview or start trailer
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(false)
                .catch { }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> {
                            val updateTime = result.result.updateDateTime
                            if (updateTime.isNotBlank()) {
                                _updateAvailable.value = true
                            }
                        }
                    }
                }
        }
    }
}
