package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.toResult
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.domain.interactor.MobileCinemaInteractor
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
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _mainContent = MutableSharedFlow<MainPageContent>()
    val mainContent = _mainContent.asSharedFlow()

    fun restartLoading() {
        viewModelScope.launch {
            interactor.getAllVideos()
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { throwable -> throwable.getFullError() }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> content.throwable.getFullError()
                        is DataResult.Success -> {
                            _mainContent.emit(content.result)
                        }
                    }
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
                .catch { throwable -> throwable.getFullError() }
                .collect { content ->
                    when (content) {
                        is DataResult.Error -> content.throwable.getFullError()
                        is DataResult.Success -> {
                            _mainContent.emit(content.result)
                        }
                    }
                }
        }
    }

    fun onTypeChanged(menuLink: VideoMenuLink?) {
        viewState.showLoading(true)
        mainScope().launch {
            videoRepository.getTypedVideos(menuLink?.searchUrl)
                .onCompletion { viewState.showLoading(false) }
                .catch { viewState.showMainContentError(getFullError(it)) }
                .collect { viewState.showMainContent(it) }
        }.addToCompositeJob()
    }

    fun selectHost(source: String) {
        videoRepository.setHost(source)
        restartLoading()
    }

    fun requestHosts() {
        mainScope().launch {
            videoRepository.getAllHosts()
                .catch { viewState.showMainContentError(getFullError(it)) }
                .collect {
                    viewState.chooseHost(it)
                }
        }.addToCompositeJob()
    }

    private fun searchCached(searchText: String) {
        mainScope().launch {
            viewState.showLoading(true)
            videoRepository.searchCached(searchText)
                .onCompletion { viewState.showLoading(false) }
                .catch { viewState.showMainContentError(getFullError(it)) }
                .collect { viewState.showMainContent(MainPageContent(movies = it).toResult()) }
        }.addToCompositeJob()
    }
}