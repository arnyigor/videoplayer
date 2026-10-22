package com.arny.mobilecinema.presentation.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.playback.NextEpisodeResolver
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.domain.playback.ResumeTarget
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

// ============ UIState ============
data class TvDetailsUiState(
    val movie: Movie? = null,
    val isLoading: Boolean = false,
    val isFavorite: Boolean = false,
    val resumeTarget: ResumeTarget? = null,
    /** Прогресс по всем сериям тайтла: статусы карточек эпизодов считаются из него. */
    val progress: Map<String, PlaybackProgress> = emptyMap(),
    val error: Boolean = false,
)

// ============ Actions (One-time effects) ============
sealed interface TvDetailsAction {
    data class ShowToast(val message: IWrappedString) : TvDetailsAction
    data class ShowError(val message: IWrappedString) : TvDetailsAction
    data class NavigateToUpdate(val url: String) : TvDetailsAction
}

class TvDetailsViewModel(
    private val moviesInteractor: MoviesInteractor,
    private val progressStore: PlaybackProgressStore,
    private val resolver: NextEpisodeResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvDetailsUiState())
    val uiState = _uiState.asStateFlow()

    // Separate flows for backwards compatibility
    val movie = MutableStateFlow<Movie?>(null)
    val isFavorite = MutableStateFlow(false)

    private val _actions = Channel<TvDetailsAction>(Channel.BUFFERED)
    val actions: Flow<TvDetailsAction> = _actions.receiveAsFlow()

    private var currentMovieId: Long = -1

    /**
     * Выбранный сезон деталей. Живёт во ViewModel, чтобы возврат из плеера не сбрасывал
     * сезон и список серий на первый (ТЗ §9.2, `SERIES-FOCUS-02`).
     */
    var selectedSeasonIndex: Int = 0
        private set

    fun selectSeason(seasonIndex: Int) {
        selectedSeasonIndex = seasonIndex
    }

    /** Позиция прокрутки деталей: возврат из плеера не должен отправлять экран наверх. */
    var detailsScrollIndex: Int = 0
        private set
    var detailsScrollOffset: Int = 0
        private set

    fun saveDetailsScroll(index: Int, offset: Int) {
        detailsScrollIndex = index
        detailsScrollOffset = offset
    }

    fun loadMovie(dbId: Long) {
        currentMovieId = dbId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                moviesInteractor.getMovie(dbId).collectLatest { result ->
                    when (result) {
                        is DataResult.Success -> {
                            val movieData = result.result
                            _uiState.value = _uiState.value.copy(
                                movie = movieData,
                                isLoading = false
                            )
                            movie.value = movieData
                            // Ленивая миграция прогресса старого формата (DOM-08).
                            runCatching { progressStore.migrateLegacy(movieData) }
                                .onFailure { Timber.e(it, "legacy progress migration failed") }
                            loadProgress(movieData)
                        }
                        is DataResult.Error -> {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = true)
                            _actions.send(TvDetailsAction.ShowError(ThrowableString(result.throwable)))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = true)
                _actions.send(TvDetailsAction.ShowError(ThrowableString(e)))
            }
        }

        viewModelScope.launch {
            try {
                moviesInteractor.isFavorite(dbId).collectLatest { result ->
                    when (result) {
                        is DataResult.Success -> {
                            _uiState.value = _uiState.value.copy(isFavorite = result.result)
                            isFavorite.value = result.result
                        }
                        is DataResult.Error -> {
                            _actions.send(TvDetailsAction.ShowError(ThrowableString(result.throwable)))
                        }
                    }
                }
            } catch (e: Exception) {
                _actions.send(TvDetailsAction.ShowError(ThrowableString(e)))
            }
        }
    }

    /** Повторная загрузка тайтла после ошибки или обновления данных. */
    fun retry() {
        if (currentMovieId <= 0) return
        _uiState.update { it.copy(error = false, isLoading = true) }
        loadMovie(currentMovieId)
    }

    /** Прогресс мог измениться после просмотра: перечитываем при возврате на экран. */
    fun refreshProgress() {
        val movie = _uiState.value.movie ?: return
        loadProgress(movie)
    }

    private fun loadProgress(movie: Movie) {
        viewModelScope.launch {
            val progress = runCatching { progressStore.progressForMovie(movie.dbId) }
                .onFailure { Timber.e(it, "failed to read playback progress") }
                .getOrDefault(emptyMap())
            _uiState.update {
                it.copy(
                    progress = progress,
                    resumeTarget = resolver.resumeTarget(movie, progress),
                )
            }
        }
    }

    fun toggleFavorite(movieId: Long) {
        viewModelScope.launch {
            try {
                moviesInteractor.onFavoriteToggle(movieId).collectLatest { result ->
                    when (result) {
                        is DataResult.Success -> {
                            _uiState.value = _uiState.value.copy(isFavorite = result.result)
                            isFavorite.value = result.result

                            val message = if (result.result) {
                                ResourceString(R.string.added_to_favorites)
                            } else {
                                ResourceString(R.string.removed_from_favorites)
                            }
                            _actions.send(TvDetailsAction.ShowToast(message))
                        }
                        is DataResult.Error -> {
                            _actions.send(TvDetailsAction.ShowError(ThrowableString(result.throwable)))
                        }
                    }
                }
            } catch (e: Exception) {
                _actions.send(TvDetailsAction.ShowError(ThrowableString(e)))
            }
        }
    }

    fun updateMovieData(movieId: Long) {
        viewModelScope.launch {
            val movie = _uiState.value.movie ?: return@launch
            val pageUrl = movie.pageUrl
            val baseUrl = moviesInteractor.getBaseUrl()
            val fullUrl = "$baseUrl/$pageUrl"

            // Отправляем action для запуска UpdateService через Fragment
            _actions.send(TvDetailsAction.NavigateToUpdate(fullUrl))
        }
    }
}