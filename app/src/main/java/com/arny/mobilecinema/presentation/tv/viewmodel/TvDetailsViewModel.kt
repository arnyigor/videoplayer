package com.arny.mobilecinema.presentation.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TvDetailsViewModel(
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {

    private val _movie = MutableStateFlow<Movie?>(null)
    val movie = _movie.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite = _isFavorite.asStateFlow()

    private var currentMovieId: Long = -1

    fun loadMovie(dbId: Long) {
        currentMovieId = dbId
        viewModelScope.launch {
            moviesInteractor.getMovie(dbId)
                .collectLatest { result ->
                    if (result is DataResult.Success) {
                        _movie.value = result.result
                    }
                }
        }
        viewModelScope.launch {
            moviesInteractor.isFavorite(dbId)
                .collectLatest { result ->
                    if (result is DataResult.Success) {
                        _isFavorite.value = result.result
                    }
                }
        }
    }

    fun toggleFavorite(movieId: Long) {
        viewModelScope.launch {
            moviesInteractor.onFavoriteToggle(movieId)
                .collectLatest { result ->
                    if (result is DataResult.Success) {
                        _isFavorite.value = result.result
                    }
                }
        }
    }
}
