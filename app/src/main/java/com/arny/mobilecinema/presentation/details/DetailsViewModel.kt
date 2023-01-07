package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.interactors.MobileCinemaInteractor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val interactor: MobileCinemaInteractor
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _data = MutableSharedFlow<DataResult<Movie>>()
    val data = _data.asSharedFlow()
    private val _cached = MutableStateFlow(false)
    val cached = _cached.asStateFlow()
    private var isRemovedFromCache = false

    fun loadVideo(movie: Movie) {
        viewModelScope.launch {
            interactor.loadMovie(movie)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .collect { content ->
                    _data.emit(content)
                }
        }
    }

    fun cacheMovie(movie: Movie?) {
        viewModelScope.launch {
            if (!isRemovedFromCache) {
                interactor.cacheMovie(movie)
                    .onStart { _loading.value = true }
                    .onCompletion { _loading.value = false }
                    .collect { content ->
                        when (content) {
                            is DataResult.Error -> {}
                            is DataResult.Success -> {
                                _cached.value = content.result == true
                            }
                        }
                    }
            }
        }
    }

    fun clearCache(movie: Movie?) {
        viewModelScope.launch {
            interactor.clearCache(movie)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> {
                            isRemovedFromCache = true
                        }
                    }
                }
        }
    }
}
