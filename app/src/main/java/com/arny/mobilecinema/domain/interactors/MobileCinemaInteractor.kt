package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.models.HostsData
import kotlinx.coroutines.flow.Flow

interface MobileCinemaInteractor {
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun searchMovie(search: String): Flow<DataResult<MainPageContent>>
    fun getTypedVideos(searchUrl: String?): Flow<DataResult<MainPageContent>>
    fun getAllHosts(): Flow<DataResult<HostsData>>
    fun setHost(source: String)
    fun searchCached(searchText: String): Flow<DataResult<List<Movie>>>
    fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>>
    fun clearCache(movie: Movie?): Flow<DataResult<Boolean>>
    fun getAllCached(): Flow<DataResult<List<Movie>>>
}