package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerViewModel @Inject constructor(
    private val interactor: MoviesInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()
    private val _isPipModeEnable = MutableStateFlow(false)
    private val _pipMode = MutableSharedFlow<Boolean>()
    val pipMode = _pipMode.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()

    fun saveCurrentCinemaPosition(position: Long, dbId: Long?) {
        viewModelScope.launch {
            val playerUiState = _uiState.value
            when {
                playerUiState.movie?.type == MovieType.CINEMA && !playerUiState.isTrailer -> {
                    savePosition(dbId, position)
                }

                playerUiState.movie?.type == MovieType.CINEMA && playerUiState.isTrailer -> {
                    _uiState.value = _uiState.value.copy(
                        isTrailer = false
                    )
                }
            }
        }
    }

    private suspend fun savePosition(dbId: Long?, position: Long) {
        interactor.saveCinemaPosition(dbId, position)
    }

    fun setPlayData(
        path: String?,
        movie: Movie?,
        seasonIndex: Int,
        episodeIndex: Int,
        trailer: Boolean
    ) {
        viewModelScope.launch {
            interactor.getSaveData(movie?.dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collect {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }

                        is DataResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                path = path,
                                movie = movie,
                                position = it.result.position,
                                season = seasonIndex,
                                episode = episodeIndex,
                                isTrailer = trailer
                            )
                        }
                    }
                }
        }
    }

    fun saveCurrentSerialPosition(dbId: Long?, season: Int, episode: Int, episodePosition: Long) {
        viewModelScope.launch {
            if (_uiState.value.movie?.type == MovieType.SERIAL) {
                interactor.saveSerialPosition(dbId, season, episode, episodePosition)
            }
        }
    }

    fun updatePipModeEnable() {
        _isPipModeEnable.value = interactor.isPipModeEnable()
    }

    fun requestPipMode() {
        viewModelScope.launch {
            if (_isPipModeEnable.value) {
                _pipMode.emit(true)
            }
        }
    }
}