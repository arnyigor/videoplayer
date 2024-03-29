package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class SplashViewModel @AssistedInject constructor(interactor: DataUpdateInteractor) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    init {
        viewModelScope.launch {
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