package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class SplashViewModel(interactor: DataUpdateInteractor) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            flow { emit(interactor.checkBaseUrl()) }
                .collectLatest {
                    _ready.value = true
                }
        }
    }
}