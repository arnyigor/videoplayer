package com.arny.mobilecinema.presentation.playerview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()

    fun setPosition(position: Long) {
        _uiState.update { currentState ->
            currentState.copy(position = position)
        }
    }

    fun setPath(path: String) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(path = path)
            }
        }
    }
}