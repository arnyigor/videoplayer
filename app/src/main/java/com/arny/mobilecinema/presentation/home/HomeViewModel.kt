package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.domain.interactor.MobileCinemaInteractor
import com.arny.mobilecinema.domain.models.HostsData
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
    private val interactor: MobileCinemaInteractor,
) : ViewModel() {
    private val _hostData = MutableSharedFlow<HostsData>()
    val hostData = _hostData.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _mainContent = MutableSharedFlow<DataResult<MainPageContent>>()
    val mainContent = _mainContent.asSharedFlow()

    fun restartLoading() {
        viewModelScope.launch {
            interactor.getAllVideos()
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    _mainContent.emit(content)
                }
        }
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
                    _mainContent.emit(content)
                }
        }
    }

    private suspend fun setError(throwable: Throwable) {
        _mainContent.emit(DataResult.Error(throwable))
    }

    fun onTypeChanged(menuLink: VideoMenuLink?) {
        viewModelScope.launch {
            interactor.getTypedVideos(menuLink?.searchUrl)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> setError(throwable) }
                .collect { content ->
                    _mainContent.emit(content)
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
                    when (result) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> result.result?.let { _hostData.emit(it) }
                    }
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
                    when (content) {
                        is DataResult.Error -> {
                            setError(content.throwable)
                        }

                        is DataResult.Success -> {
                            _mainContent.emit(DataResult.Success(MainPageContent(movies = content.result)))
                        }
                    }
                }
        }
    }
}