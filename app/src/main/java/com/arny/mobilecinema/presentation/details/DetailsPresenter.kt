package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.VideoRepository
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.di.models.Movie
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
class DetailsPresenter @Inject constructor(
    private val videoRepository: VideoRepository
) : BaseMvpPresenter<DetailsView>() {
    private var data: DataResult<Movie>? = null
    private var isRemovedFromCache = false

    fun clearCache(movie: Movie?) {
        mainScope().launch {
            videoRepository.clearCache(movie)
                .catch { viewState.showError(getFullError(it)) }
                .collect {
                    isRemovedFromCache = true
                }
        }.addToCompositeJob()
    }

    fun cacheMovie(movie: Movie?) {
        mainScope().launch {
            if (!isRemovedFromCache) {
                videoRepository.cacheMovie(movie)
                    .catch { viewState.showError(getFullError(it)) }
                    .collect {}
            }
        }.addToCompositeJob()
    }

    fun loadVideo(movie: Movie) {
        mainScope().launch {
            if (data == null) {
                viewState.showLoading(true)
                videoRepository.loadMovie(movie)
                    .onCompletion { viewState.showLoading(false) }
                    .catch { viewState.showError(getFullError(it)) }
                    .collect { res ->
                        data = res
                        viewState.showVideo(data)
                    }
            } else {
                viewState.showVideo(data)
            }
        }.addToCompositeJob()
    }
}