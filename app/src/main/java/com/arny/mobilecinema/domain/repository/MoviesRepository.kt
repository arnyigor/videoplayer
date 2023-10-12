package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie

interface MoviesRepository {
    var order: String
    var prefHistoryOnCache: Boolean
    var prefPipMode: Boolean
    fun getMovies(
        search: String,
        order: String,
        searchType: String,
        searchAddTypes: List<String>,
    ): Pager<Int, ViewMovie>
    fun getMovie(id: Long): Movie?
    fun getSaveData(movieDbId: Long?): HistoryEntity?
    fun insertCinemaPosition(movieDbId: Long, position: Long): Boolean
    fun updateCinemaPosition(movieDbId: Long?, position: Long): Boolean
    fun insertSerialPosition(movieDbId: Long, season: Int, episode: Int, episodePosition: Long): Boolean
    fun updateSerialPosition(movieDbId: Long?, season: Int, episode: Int, episodePosition: Long): Boolean
    fun getHistoryMovies(search: String, order: String, searchType: String): Pager<Int, ViewMovie>
    fun clearViewHistory(movieDbId: Long?): Boolean
    suspend fun isHistoryEmpty(): Boolean
    fun saveOrder(order: String)
    fun clearAllViewHistory():Boolean
    suspend fun isMoviesEmpty(): Boolean
    suspend fun getGenres(): List<String>
    fun getMinMaxYears(): SimpleIntRange
    fun getCountries(): List<String>
}