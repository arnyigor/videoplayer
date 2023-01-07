package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun setPosition(position: Long) {
        _uiState.update { currentState ->
            currentState.copy(position = position)
        }
    }

    fun setPath(path: String) {
        _uiState.update { currentState ->
            currentState.copy(path = path)
        }
    }
}