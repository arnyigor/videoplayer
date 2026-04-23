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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val path: String? = null,
    val time: Long = 0,
    val movie: Movie? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val excludeUrls: Set<String> = emptySet(),
    val version: Long = 0,
)

class PlayerViewModel(
    private val interactor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
    private val feedbackInteractor: FeedbackInteractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var lastKnownPosition: Long = 0L
    private var lastKnownSeason: Int = 0
    private var lastKnownEpisode: Int = 0

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

    private val _requestUpdate = MutableSharedFlow<Unit>()
    val requestUpdate = _requestUpdate.asSharedFlow()

    private val _cachedResizeModeIndex = MutableStateFlow(0)
    val cachedResizeModeIndex = _cachedResizeModeIndex.asStateFlow()

fun setPlayData(
        path: String?,
        movie: Movie?,
        seasonIndex: Int,
        episodeIndex: Int,
    ) {
        val movieId = movie?.dbId

        viewModelScope.launch {
            val dataResult = historyInteractor.getSaveData(movieId)
                .catch { _error.trySend(ThrowableString(it)) }
                .first()

            when (dataResult) {
                is DataResult.Error -> {
                    _error.trySend(ThrowableString(dataResult.throwable))
                }

                is DataResult.Success -> {
                    val saveData = dataResult.result

                    val (resolvedSeason, resolvedEpisode, resolvedTime) = resolvePosition(
                        movie = movie,
                        saveData = saveData,
                        argsSeason = seasonIndex,
                        argsEpisode = episodeIndex,
                    )

                    lastKnownPosition = resolvedTime
                    lastKnownSeason = resolvedSeason
                    lastKnownEpisode = resolvedEpisode

                    _uiState.update { current ->
                        PlayerUiState(
                            path = path,
                            movie = movie,
                            time = resolvedTime,
                            season = resolvedSeason,
                            episode = resolvedEpisode,
                            version = current.version + 1,
                        )
                    }
                }
            }
        }
    }

private fun resolvePosition(
        movie: Movie?,
        saveData: SaveData,
        argsSeason: Int,
        argsEpisode: Int,
    ): Triple<Int, Int, Long> {
        if (movie == null) {
            return Triple(0, 0, 0L)
        }

        return when (movie.type) {
            MovieType.CINEMA -> {
                Triple(0, 0, saveData.time)
            }

            MovieType.SERIAL -> {
                val hasSavedPosition = saveData.movieDbId != null && saveData.time > 0
                val savedSeason = saveData.seasonPosition.coerceAtLeast(0)
                val savedEpisode = saveData.episodePosition.coerceAtLeast(0)

                // Проверяем, выбрал ли пользователь тот же эпизод что сохранён
                val sameAsSaved = argsSeason == savedSeason && argsEpisode == savedEpisode

                when {
                    // Нет сохранённой позиции - начинаем сначала
                    !hasSavedPosition -> Triple(
                        argsSeason.coerceAtLeast(0),
                        argsEpisode.coerceAtLeast(0),
                        0L
                    )

                    // Пользователь выбрал ТОТ ЖЕ эпизод что сохранён - продолжаем с времени
                    sameAsSaved -> Triple(
                        savedSeason,
                        savedEpisode,
                        saveData.time
                    )

                    // Пользователь выбрал ДРУГОЙ эпизод - начинаем сначала (с time=0)
                    else -> Triple(
                        argsSeason.coerceAtLeast(0),
                        argsEpisode.coerceAtLeast(0),
                        0L
                    )
                }
            }

            else -> Triple(0, 0, 0L)
        }
    }

    fun saveMoviePosition(
        dbId: Long?,
        time: Long,
        season: Int,
        episode: Int
    ) {
        viewModelScope.launch {
            if (dbId != null) {
                val state = _uiState.value
                when (state.movie?.type) {
                    MovieType.CINEMA -> {
                        val save = historyInteractor.saveCinemaPosition(dbId, time)
                        if (!save) {
                            _error.trySend(ResourceString(R.string.movie_save_error))
                        }
                        historyInteractor.setCacheChanged(true)
                    }

                    MovieType.SERIAL -> {
                        val save = historyInteractor.saveSerialPosition(
                            movieDbId = dbId,
                            playerSeasonPosition = season,
                            playerEpisodePosition = episode,
                            time = time,
                            currentSeasonPosition = season,
                            currentEpisodePosition = episode
                        )
                        if (!save) {
                            _error.trySend(ResourceString(R.string.movie_save_error))
                        }
                        historyInteractor.setCacheChanged(true)
                    }

                    else -> {}
                }
            }
        }
    }

    fun updateCurrentEpisode(season: Int, episode: Int) {
        _uiState.update { state ->
            state.copy(
                season = season,
                episode = episode
            )
        }
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
                if (serialEpisode != null) {
                    nextCinemaUrl = when {
                        serialEpisode.hls.isNotBlank() && serialEpisode.hls !in excludeUrls -> serialEpisode.hls
                        serialEpisode.dash.isNotBlank() && serialEpisode.dash !in excludeUrls -> serialEpisode.dash
                        else -> null
                    }
                }

                hasAnyUrls = nextCinemaUrl != null ||
                        state.movie.seasons.any { season ->
                            season.episodes.any { ep ->
                                (ep.hls.isNotBlank() && ep.hls !in excludeUrls) ||
                                        (ep.dash.isNotBlank() && ep.dash !in excludeUrls)
                            }
                        }
            }

            else -> {}
        }

        if (hasAnyUrls) {
            _toast.trySend(ResourceString(R.string.try_open_next_link))
            _uiState.value = state.copy(
                path = nextCinemaUrl,
                excludeUrls = excludeUrls,
                version = state.version + 1,
            )
        } else if (state.movie?.let { movie ->
                when (movie.type) {
                    MovieType.CINEMA -> movie.getAllCinemaUrls().all { it.isBlank() }
                    MovieType.SERIAL -> movie.seasons.all { season ->
                        season.episodes.all { ep -> ep.hls.isBlank() && ep.dash.isBlank() }
                    }

                    else -> true
                }
            } == true
        ) {
            _toast.trySend(ResourceString(R.string.update_available_title))
            viewModelScope.launch {
                _requestUpdate.emit(Unit)
            }
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

    fun updatePlaybackPosition(position: Long, season: Int, episode: Int) {
        lastKnownPosition = position
        lastKnownSeason = season
        lastKnownEpisode = episode
    }

    fun forceReEmit() {
        val currentState = _uiState.value
        if (currentState.version > 0) {
            _uiState.update { state ->
                state.copy(
                    time = lastKnownPosition.takeIf { it > 0 } ?: state.time,
                    season = lastKnownSeason.takeIf { it >= 0 } ?: state.season,
                    episode = lastKnownEpisode.takeIf { it >= 0 } ?: state.episode,
                    version = state.version + 1
                )
            }
        }
    }
}