package com.arny.mobilecinema.presentation.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModel(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    val moviesDataFlow: Flow<PagingData<ViewMovie>> = _refreshTrigger
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

    val favoriteMoviesFlow: Flow<PagingData<ViewMovie>> = _refreshTrigger
        .flatMapLatest {
            moviesInteractor.getFavoriteMovies(
                search = "",
                order = "",
                searchType = ""
            )
        }
        .cachedIn(viewModelScope)

    val historyMoviesFlow: Flow<PagingData<ViewMovie>> = _refreshTrigger
        .flatMapLatest {
            historyInteractor.getHistoryMovies(
                search = "",
                order = "",
                searchType = ""
            )
        }
        .cachedIn(viewModelScope)

    init {
        checkForUpdate()
    }

    fun refreshData() {
        _refreshTrigger.value++
    }

    fun onMovieSelected(movie: ViewMovie) {
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

    fun downloadData() {
        dataUpdateInteractor.updateAll()
    }

    fun stopUpdate() {
        dataUpdateInteractor.cancelUpdate()
    }
}