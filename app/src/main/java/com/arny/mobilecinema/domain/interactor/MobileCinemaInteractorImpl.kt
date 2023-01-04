package com.arny.mobilecinema.domain.interactor

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MobileCinemaInteractorImpl @Inject constructor(
    private val repository: VideoRepository
) : MobileCinemaInteractor{
    override fun getAllVideos(): Flow<DataResult<MainPageContent>> = repository.getAllVideos()

    override fun searchMovie(search: String): Flow<List<Movie>> = repository.searchMovie(search)
}