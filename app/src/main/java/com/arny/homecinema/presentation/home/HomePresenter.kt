package com.arny.homecinema.presentation.home

import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.data.utils.getFullError
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.VideoSearchLink
import com.arny.homecinema.di.scopes.FragmentScope
import com.arny.homecinema.presentation.utils.BaseMvpPresenter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import moxy.InjectViewState
import javax.inject.Inject

@FragmentScope
@InjectViewState
class HomePresenter @Inject constructor(
    private val videoRepository: VideoRepository
) : BaseMvpPresenter<HomeView>() {

    override fun onFirstViewAttach() {
        restartLoading()
    }

    fun restartLoading() {
        viewState.showLoading(true)
        mainScope().launch {
            videoRepository.getAllVideos()
                .onCompletion { viewState.showLoading(false) }
                .catch { viewState.showMainContentError(getFullError(it)) }
                .collect { viewState.showMainContent(it) }
        }.addToCompositeJob()
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
        viewState.showLoading(true)
        mainScope().launch {
            videoRepository.searchMovie(search)
                .onCompletion { viewState.showLoading(false) }
                .catch { viewState.showMainContentError(getFullError(it)) }
                .collect { viewState.showMainContent(MainPageContent(it).toResult()) }
        }.addToCompositeJob()
    }

    fun onTypeChanged(searchLink: VideoSearchLink?) {
        viewState.showLoading(true)
        mainScope().launch {
            videoRepository.getTypedVideos(searchLink?.searchUrl)
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
        viewState.showLoading(true)
        mainScope().launch {
            videoRepository.getAllHosts()
                .onCompletion { viewState.showLoading(false) }
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
