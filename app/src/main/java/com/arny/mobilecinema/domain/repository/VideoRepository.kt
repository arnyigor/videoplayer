package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.HostsData
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchMovie(search: String): Flow<DataResult<MainPageContent>>
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>>
    fun setHost(source: String, resetHost: Boolean)
    fun getAllHosts(): Flow<DataResult<HostsData>>
    fun onPlaylistChanged(
        seasonPosition: Int,
        episodePosition: Int
    ): Flow<DataResult<SerialEpisode>>

    fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>>
    fun clearCache(movie: Movie?): Flow<DataResult<Boolean>>
    fun searchCached(searchText: String): Flow<DataResult<List<Movie>>>
    fun getAllCached(): Flow<DataResult<List<Movie>>>
}