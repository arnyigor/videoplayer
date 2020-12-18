package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.Movie
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchMovie(search: String): Flow<MutableList<Movie>>
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun getAllVideos(type: String?): Flow<DataResult<MainPageContent>>
    fun loadMovie(movie: Movie): Flow<DataResult<Movie>>
}