package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _movie = MutableStateFlow<Movie?>(null)
    val movie = _movie.asSharedFlow()
    private val _saveData = MutableSharedFlow<SaveData>()
    val saveData = _saveData.asSharedFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()
    private val _addToHistory = MutableSharedFlow<Boolean>()
    val addToHistory = _addToHistory.asSharedFlow()

    fun loadVideo(id: Long) {
        viewModelScope.launch {
            interactor.getMovie(id)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { _error.emit(ThrowableString(it)) }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(result.throwable))
                        }

                        is DataResult.Success -> {
                            _movie.value = result.result
                        }
                    }
                }
        }
    }

    fun loadSaveData(dbId: Long) {
        viewModelScope.launch {
            interactor.getSaveData(dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success ->
                            _saveData.emit(it.result)
                    }
                }
        }
    }

    fun clearViewHistory() {
        viewModelScope.launch {
            val mMovie = _movie.value
            interactor.clearViewHistory(mMovie?.dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success -> {
                            val removed = it.result
                            if (removed) {
                                when (mMovie?.type) {
                                    MovieType.CINEMA -> {
                                        _toast.emit(ResourceString(R.string.movie_cache_cleared))
                                    }
                                    MovieType.SERIAL -> {
                                        _toast.emit(ResourceString(R.string.serial_cache_cleared))
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
        }
    }

    fun addToHistory() {
        viewModelScope.launch {
            val mMovie = _movie.value
            interactor.addToHistory(mMovie?.dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success -> {
                            _addToHistory.emit(it.result)
                        }
                    }
                }
        }
    }
}
