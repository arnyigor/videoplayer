package com.arny.mobilecinema.domain.interactor

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import kotlinx.coroutines.flow.Flow

interface MobileCinemaInteractor {
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun searchMovie(search: String): Flow<List<Movie>>
}