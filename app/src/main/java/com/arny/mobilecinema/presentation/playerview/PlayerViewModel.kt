package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerViewModel : ViewModel() {
    private var playerData = PlayerData()
    private val _uiState = MutableStateFlow(PlayerUiState(playerData))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun setPosition(position: Long) {
        _uiState.update { currentState ->
            currentState.copy(
                playerData = playerData.copy(
                    position = position
                )
            )
        }
    }

    fun setPath(path: String) {
        _uiState.update { currentState ->
            currentState.copy(
                playerData = playerData.copy(
                    path = path
                )
            )
        }
    }
}