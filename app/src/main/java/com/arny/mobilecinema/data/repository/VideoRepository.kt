package com.arny.mobilecinema.data.repository

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.MainPageContent
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.SerialEpisode
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchMovie(search: String): Flow<List<Movie>>
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>>
    fun loadMovie(movie: Movie): Flow<DataResult<Movie>>
    fun setHost(source: String, resetHost: Boolean = true)
    fun getAllHosts(): Flow<DataResult<Pair<Array<String>, Int>>>
    fun onPlaylistChanged(
        seasonPosition: Int,
        episodePosition: Int
    ): Flow<DataResult<SerialEpisode?>>

    fun cacheMovie(movie: Movie?): Flow<DataResult<Boolean>>
    fun clearCache(movie: Movie?): Flow<DataResult<Boolean>>
    fun searchCached(searchText: String): Flow<List<Movie>>
    fun getAllCached(): Flow<DataResult<List<Movie>>>
}