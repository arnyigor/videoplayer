package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.data.repository.VideoRepository
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.utils.BaseMvpPresenter
import moxy.InjectViewState
import javax.inject.Inject

@FragmentScope
@InjectViewState
class DetailsPresenter @Inject constructor(
    private val videoRepository: VideoRepository
) : BaseMvpPresenter<DetailsView>() {

    fun clearCache(movie: Movie?) {

    }
}