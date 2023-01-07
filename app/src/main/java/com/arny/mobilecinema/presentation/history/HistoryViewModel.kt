package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.interactors.MobileCinemaInteractor
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
    private val interactor: MobileCinemaInteractor
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _mainContent = MutableSharedFlow<DataResult<MainPageContent>>()
    val mainContent = _mainContent.asSharedFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()

    init {
        loadHistory()
    }

    private suspend fun setError(throwable: Throwable) {
        _mainContent.emit(DataResult.Error(throwable))
    }

    fun loadHistory() {
        viewModelScope.launch {
            interactor.getAllCached()
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> setError(content.throwable)
                        is DataResult.Success -> {
                            _mainContent.emit(DataResult.Success(MainPageContent(content.result)))
                        }
                    }
                }
        }
    }

    fun searchCached(searchText: String) {
        viewModelScope.launch {
            if (searchText.isBlank()) {
                loadHistory()
            } else {
                interactor.searchCached(searchText)
                    .onStart { _loading.value = true }
                    .onCompletion { _loading.value = false }
                    .catch { throwable -> setError(throwable) }
                    .collect { content ->
                        when (content) {
                            is DataResult.Error -> setError(content.throwable)
                            is DataResult.Success -> {
                                _mainContent.emit(DataResult.Success(MainPageContent(content.result)))
                            }
                        }
                    }
            }
        }
    }

    fun clearCache(movie: Movie?) {
        viewModelScope.launch {
            interactor.clearCache(movie)
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> setError(content.throwable)
                        is DataResult.Success -> {
                            _toast.emit(ResourceString(R.string.movie_cleared))
                            loadHistory()
                        }
                    }
                }
        }
    }
}