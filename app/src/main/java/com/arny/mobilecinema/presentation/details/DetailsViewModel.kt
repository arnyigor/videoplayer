package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
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
    private val _movie = MutableSharedFlow<Movie>()
    val movie = _movie.asSharedFlow()
    private val _saveData = MutableSharedFlow<SaveData>()
    val saveData = _saveData.asSharedFlow()

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

    fun loadSaveData(dbId: Long) {
        viewModelScope.launch {
            _saveData.emit(interactor.getSaveData(dbId))
        }
    }
}
