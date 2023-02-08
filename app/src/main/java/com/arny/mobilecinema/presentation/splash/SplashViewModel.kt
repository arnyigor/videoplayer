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
            interactor.checkBaseUrl()
                .collectLatest {
                    _ready.value = true
                }
        }
    }
}