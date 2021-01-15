package com.arny.mobilecinema.presentation.home

import com.arny.mobilecinema.data.models.toResult
import com.arny.mobilecinema.data.repository.VideoRepository
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.utils.BaseMvpPresenter
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
