package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Состояние старта: проверка зеркала, успех или «сайт недоступен» с возможностью повтора. */
sealed interface SplashState {
    data object Checking : SplashState
    data object Ready : SplashState
    data object SiteUnavailable : SplashState
}

class SplashViewModel constructor(
    private val interactor: DataUpdateInteractor
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Checking)
    val state = _state.asStateFlow()

    private val _readyComplete = MutableStateFlow(false)
    val readyComplete = _readyComplete.asStateFlow()

    private val _sharedUrl = MutableStateFlow<String?>(null)
    val sharedUrl: String? get() = _sharedUrl.value

    init {
        checkBaseUrl()
    }

    fun onIntentUrl(url: String) {
        // Activity extras survive cold start; an unobserved SharedFlow event does not.
        _sharedUrl.value = url
    }

    private fun checkBaseUrl() {
        viewModelScope.launch {
            runCatching {
                interactor.checkBaseUrl().collect { result ->
                    // Явный «сайт недоступен» вместо тихого старта с мёртвым зеркалом.
                    val resolved = result is DataResult.Success && result.result
                    if (resolved) {
                        _state.value = SplashState.Ready
                        _readyComplete.value = true
                    } else if (!_readyComplete.value) {
                        _state.value = SplashState.SiteUnavailable
                    }
                }
            }.onFailure {
                if (!_readyComplete.value) _state.value = SplashState.SiteUnavailable
            }
        }
    }
}
