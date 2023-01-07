package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.models.HostsData
import com.arny.mobilecinema.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MobileCinemaInteractorImpl @Inject constructor(
    private val repository: VideoRepository
) : MobileCinemaInteractor{
    override fun getAllVideos(): Flow<DataResult<MainPageContent>> = repository.getAllVideos()

    override fun searchMovie(search: String): Flow<DataResult<MainPageContent>> =
        repository.searchMovie(search)

    override fun getTypedVideos(searchUrl: String?): Flow<DataResult<MainPageContent>> =
        repository.getTypedVideos(searchUrl)

    override fun getAllHosts(): Flow<DataResult<HostsData>> = repository.getAllHosts()

    override fun setHost(source: String) {
        repository.setHost(source, true)
    }

    override fun searchCached(searchText: String): Flow<DataResult<List<Movie>>> =
        repository.searchCached(searchText)

    override fun loadMovie(movie: Movie): Flow<DataResult<Movie>> = repository.loadMovie(movie)

    override fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>> =
        repository.cacheMovie(movie)

    override fun clearCache(movie: Movie?): Flow<DataResult<Boolean>> =
        repository.clearCache(movie)

    override fun getAllCached(): Flow<DataResult<List<Movie>>> = repository.getAllCached()
}