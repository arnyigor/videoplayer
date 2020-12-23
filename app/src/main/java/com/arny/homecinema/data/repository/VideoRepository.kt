package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.SerialEpisode
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchMovie(search: String): Flow<MutableList<Movie>>
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun getTypedVideos(type: String?): Flow<DataResult<MainPageContent>>
    fun loadMovie(movie: Movie, cache: Boolean): Flow<DataResult<Movie>>
    fun setHost(source: String,resetHost:Boolean = true)
    fun getAllHosts(): Flow<DataResult<Pair<Array<String>, Int>>>
    fun onSeasonChanged(position: Int): Flow<DataResult<List<SerialEpisode>>>
    fun onEpisodeChanged(position: Int): Flow<DataResult<SerialEpisode?>>
}