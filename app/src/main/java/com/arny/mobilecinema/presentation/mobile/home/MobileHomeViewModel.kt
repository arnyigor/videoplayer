package com.arny.mobilecinema.presentation.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.PrefsConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Данные Compose-главной (MOB-09C).
 *
 * Витрина грузится один раз (и по кнопке «Повторить»), а «Продолжить просмотр» приходит
 * потоком из общего с TV стора: тик прогресса обновляет только карточки продолжения,
 * остальные ряды остаются на месте — лента не «прыгает» (ТЗ §20.3).
 */
class MobileHomeViewModel(
    private val moviesInteractor: MoviesInteractor,
    private val progressStore: PlaybackProgressStore,
    private val prefs: Prefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MobileHomeUiState(
            baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty(),
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        loadHighlights()
        observeContinueWatching()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = false) }
        loadHighlights()
    }

    private fun loadHighlights() {
        viewModelScope.launch {
            try {
                val highlights = moviesInteractor.getHomeHighlights()
                _uiState.update { it.copy(highlights = highlights, isLoading = false, error = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load home highlights")
                _uiState.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            try {
                progressStore.observeContinueWatching().collect { items ->
                    _uiState.update { state ->
                        state.copy(continueCards = items.map(ContinueCardMapper::toCard))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Лента без «Продолжить» полезнее, чем пустой экран из-за одного ряда.
                Timber.e(e, "Failed to observe continue watching")
            }
        }
    }
}
