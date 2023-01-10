package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.domain.interactors.MainInteractor
import com.arny.mobilecinema.domain.interactors.MobileCinemaInteractor
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val mainInteractor: MainInteractor,
    private val interactor: MobileCinemaInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies = _movies.asStateFlow()

    init {
        getAllData()
    }

    private fun getAllData() {
        viewModelScope.launch {
            mainInteractor.loadData()
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(content.throwable))
                        }

                        is DataResult.Success -> {
                            getMockData()
                        }
                    }
                }
        }
    }

    private fun getMockData() {
        viewModelScope.launch {
            interactor.getAllVideos()
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> setError(content.throwable)
                        is DataResult.Success -> {
                            _movies.value = content.result?.movies.orEmpty()
                        }
                    }
                }
        }
    }

    fun restartLoading() {
        getAllData()
    }

    fun search(search: String, fromCache: Boolean = false) {
        if (search.isBlank()) {
            restartLoading()
        } else {
            if (fromCache) {
                searchCached(search)
            }else{
                searchVideo(search)
            }
        }
    }

    private fun searchVideo(search: String) {
        viewModelScope.launch {
            interactor.searchMovie(search)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                }
        }
    }

    private suspend fun setError(throwable: Throwable) {
        _error.emit(ThrowableString(throwable))
    }

    fun onTypeChanged(menuLink: VideoMenuLink?) {
        viewModelScope.launch {
            interactor.getTypedVideos(menuLink?.searchUrl)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                }
        }
    }

    fun selectHost(source: String) {
        interactor.setHost(source)
        restartLoading()
    }

    fun requestHosts() {
        viewModelScope.launch {
            interactor.getAllHosts()
                .catch { throwable -> setError(throwable) }
                .collect { result ->
                }
        }
    }

    private fun searchCached(searchText: String) {
        viewModelScope.launch {
            interactor.searchCached(searchText)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                }
        }
    }
}