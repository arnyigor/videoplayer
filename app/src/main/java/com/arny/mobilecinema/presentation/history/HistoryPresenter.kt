package com.arny.mobilecinema.presentation.history

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.toResult
import com.arny.mobilecinema.domain.repository.VideoRepository
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.utils.BaseMvpPresenter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import moxy.InjectViewState
import javax.inject.Inject

@FragmentScope
@InjectViewState
class HistoryPresenter @Inject constructor(
    private val videoRepository: VideoRepository
) : BaseMvpPresenter<HistoryView>() {

    override fun onFirstViewAttach() {
        loadHistory()
    }

    fun loadHistory() {
        mainScope().launch {
            videoRepository.getAllCached()
                .catch { viewState.showError(getFullError(it)) }
                .collect { viewState.updateList(it) }
        }.addToCompositeJob()
    }

    fun searchCached(searchText: String) {
        mainScope().launch {
            if (searchText.isBlank()) {
                loadHistory()
            } else {
                videoRepository.searchCached(searchText)
                    .catch { viewState.showError(getFullError(it)) }
                    .collect { viewState.updateList(it.toResult()) }
            }
        }.addToCompositeJob()
    }

    fun clearCache(movie: Movie?){
        mainScope().launch {
            videoRepository.clearCache(movie)
                .catch { viewState.showError(getFullError(it)) }
                .collect {
                    viewState.toastMessage(R.string.movie_cleared)
                    loadHistory()
                }
        }.addToCompositeJob()
    }
}
