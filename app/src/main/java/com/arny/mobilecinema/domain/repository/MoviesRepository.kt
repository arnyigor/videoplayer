package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie

interface MoviesRepository {
    var orderPref: String
    var historyOrderPref: String
    var pipModePref: Boolean
    fun getMovies(
        search: String,
        order: String,
        searchType: String = "",
        searchAddTypes: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        years: SimpleIntRange? = null,
        imdbs: SimpleFloatRange? = null,
        kps: SimpleFloatRange? = null,
        likesPriority: Boolean,
    ): Pager<Int, ViewMovie>

    fun getMovie(id: Long): Movie?
    fun getMovie(pageUrl: String): Movie?
    fun getSaveData(movieDbId: Long?): HistoryEntity?
    fun insertCinemaPosition(movieDbId: Long, position: Long, currentTimeMillis: Long): Boolean
    fun updateCinemaPosition(movieDbId: Long?, position: Long, currentTimeMillis: Long): Boolean
    fun insertSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        episodePosition: Long,
        currentTimeMs: Long
    ): Boolean

    fun updateSerialPosition(
        movieDbId: Long?,
        season: Int,
        episode: Int,
        time: Long,
        currentTimeMs: Long
    ): Boolean

    fun getHistoryMovies(search: String, order: String, searchType: String): Pager<Int, ViewMovie>
    fun clearViewHistory(movieDbId: Long?): Boolean
    suspend fun isHistoryEmpty(): Boolean
    fun saveOrder(order: String)
    fun saveHistoryOrder(order: String)
    fun clearAllViewHistory(): Boolean
    suspend fun isMoviesEmpty(): Boolean
    fun getGenres(): List<String>
    fun getMinMaxYears(): SimpleIntRange
    fun getCountries(): List<String>
}