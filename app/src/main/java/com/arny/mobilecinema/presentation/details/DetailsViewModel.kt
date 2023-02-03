package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _movie = MutableSharedFlow<Movie>()
    val movie = _movie.asSharedFlow()

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
                            _movie.emit(result.result)
                        }
                    }
                }
        }
    }
}
