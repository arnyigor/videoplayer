package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SplashViewModel(interactor: DataUpdateInteractor) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            _ready.value = true
            interactor.checkBaseUrl()
                .catch {
                    _ready.value = true
                }
                .collectLatest {
                    _ready.value = true
                }
        }
    }
}