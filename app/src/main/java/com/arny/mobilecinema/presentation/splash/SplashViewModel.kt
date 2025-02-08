package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SplashViewModel @AssistedInject constructor(
    private val interactor: DataUpdateInteractor
) : ViewModel() {

    private val _checkBaseUrl = MutableStateFlow(false)
    val readyComplete = _checkBaseUrl.asStateFlow()

    init {
        viewModelScope.launch {
            interactor.checkBaseUrl()
                .catch {
                    _checkBaseUrl.value = true
                }
                .collectLatest {
                    _checkBaseUrl.value = true
                }
        }
    }

    fun onIntentUrl(url: String) {
        viewModelScope.launch {
            interactor.setIntentUrl(url)
        }
    }
}