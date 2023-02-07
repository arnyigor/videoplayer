package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()

    fun saveCurrentPosition(position: Long, dbId: Long?) {
        viewModelScope.launch {
            interactor.saveMoviePosition(dbId, position)
        }
    }

    fun setPlayData(path: String?, movie: Movie?, seasonIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val data = interactor.getSaveData(movie?.dbId)
            _uiState.update { currentState ->
                currentState.copy(
                    path = path,
                    movie = movie,
                    position = data.position,
                    season = seasonIndex,
                    episode = episodeIndex
                )
            }
        }
    }

    fun saveCurrentSerialPosition(season: Int, episode: Int, dbId: Long?) {
        viewModelScope.launch {
            interactor.saveSerialPosition(dbId, season, episode)
        }
    }
}