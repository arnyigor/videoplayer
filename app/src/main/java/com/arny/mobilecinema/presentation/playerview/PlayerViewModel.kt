package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackInteractor
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.presentation.player.getAllCinemaUrls
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PlayerViewModel @AssistedInject constructor(
    private val interactor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
    private val feedbackInteractor: FeedbackInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerMovieUiState())
    val uiState = _uiState.asStateFlow()
    private val _isPipModeEnable = MutableStateFlow(false)
    private val _pipMode = BufferedChannel<Boolean>()
    val pipMode = _pipMode.receiveAsFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = BufferedChannel<IWrappedString>()
    val error = _error.receiveAsFlow()
    private val _toast = BufferedChannel<IWrappedString>()
    val toast = _toast.receiveAsFlow()
    private val _back = MutableSharedFlow<Unit>()
    val back = _back.asSharedFlow()
    private val _cachedResizeModeIndex = MutableStateFlow(0)
    val cachedResizeModeIndex = _cachedResizeModeIndex.asStateFlow()

    fun saveMoviePosition(dbId: Long?, time: Long, season: Int, episode: Int) {
        viewModelScope.launch {
            //detect cache changed
            historyInteractor.setCacheChanged(true)

            val playerUiState = _uiState.value
            if (dbId != null) {
                when (playerUiState.movie?.type) {
                    MovieType.CINEMA -> {
                        val save = historyInteractor.saveCinemaPosition(dbId, time)
                        if (!save) {
                            _error.trySend(ResourceString(R.string.movie_save_error))
                        }
                    }

                    MovieType.SERIAL -> {
                        val state = _uiState.value
                        val save = historyInteractor.saveSerialPosition(
                            movieDbId = dbId,
                            playerSeasonPosition = season,
                            playerEpisodePosition = episode,
                            time = time,
                            currentSeasonPosition = state.season,
                            currentEpisodePosition = state.episode
                        )
                        if (!save) {
                            _error.trySend(ResourceString(R.string.movie_save_error))
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    fun setPlayData(
        path: String?,
        movie: Movie?,
        seasonIndex: Int,
        episodeIndex: Int,
    ) {
        viewModelScope.launch {
            historyInteractor.getSaveData(movie?.dbId)
                .catch { _error.trySend(ThrowableString(it)) }
                .collect { dataResult ->
                    when (dataResult) {
                        is DataResult.Error -> {
                            _error.trySend(ThrowableString(dataResult.throwable))
                        }

                        is DataResult.Success -> {
                            val saveData = dataResult.result
                            _uiState.value = _uiState.value.copy(
                                path = path,
                                movie = movie,
                                time = getTime(movie, saveData, seasonIndex, episodeIndex),
                                season = seasonIndex,
                                episode = episodeIndex,
                            )
                        }
                    }
                }
        }
    }

    private fun getTime(
        movie: Movie?,
        saveData: SaveData,
        seasonIndex: Int,
        episodeIndex: Int
    ): Long {
        return when {
            movie == null -> 0L
            movie.type == MovieType.CINEMA -> saveData.time
            movie.type == MovieType.SERIAL && saveData.seasonPosition == seasonIndex && saveData.episodePosition == episodeIndex -> saveData.time
            else -> 0L
        }
    }

    fun updatePipModeEnable() {
        _isPipModeEnable.value = interactor.isPipModeEnable()
    }

    fun requestPipMode() {
        viewModelScope.launch {
            if (_isPipModeEnable.value) {
                _pipMode.trySend(true)
            }
        }
    }

    fun setLastPlayerError(error: String) {
        feedbackInteractor.setLastError(error)
    }

    fun retryOpenCinema(
        errorUrl: String?,
        serialEpisode: SerialEpisode?,
    ) {
        val state = _uiState.value
        val excludeUrls = state.excludeUrls.toMutableSet()
        if (!errorUrl.isNullOrBlank()) {
            excludeUrls.add(errorUrl)
        }
        var nextCinemaUrl: String? = null
        var hasAnyUrls = false
        when (state.movie?.type) {
            MovieType.CINEMA -> {
                nextCinemaUrl = state.movie.getAllCinemaUrls().firstOrNull {
                    it !in excludeUrls && it.isNotBlank()
                }
                hasAnyUrls = !nextCinemaUrl.isNullOrBlank()
            }

            MovieType.SERIAL -> {
                hasAnyUrls = !excludeUrls.contains(serialEpisode?.hls) ||
                        !excludeUrls.contains(serialEpisode?.dash)
            }

            else -> {}
        }
        if (hasAnyUrls) {
            _toast.trySend(ResourceString(R.string.try_open_next_link))
            _uiState.value = state.copy(
                path = nextCinemaUrl,
                excludeUrls = excludeUrls
            )
        } else {
            _toast.trySend(ResourceString(R.string.no_any_links_to_open))
            viewModelScope.launch {
                _back.emit(Unit)
            }
        }
    }

    fun updateResizeModeIndex(resizeModeIndex: Int) {
        _cachedResizeModeIndex.value = resizeModeIndex
    }
}