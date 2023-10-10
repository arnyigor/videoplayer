package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.SimpleFloatRange
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
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        years: SimpleIntRange? = null,
        imdbs: SimpleFloatRange? = null,
        kps: SimpleFloatRange? = null,
    ): Pager<Int, ViewMovie>
    fun getMovie(id: Long): Movie?
    fun getSaveData(dbId: Long?): SaveData
    fun saveCinemaPosition(dbId: Long?, position: Long)
    fun saveSerialPosition(id: Long?, season: Int, episode: Int, episodePosition: Long)
    fun getHistoryMovies(search: String, order: String, searchType: String): Pager<Int, ViewMovie>
    fun clearViewHistory(dbId: Long?): Boolean
    suspend fun isHistoryEmpty(): Boolean
    fun saveOrder(order: String)
    fun clearAllViewHistory():Boolean
    suspend fun isMoviesEmpty(): Boolean
    suspend fun getGenres(): List<String>
    fun getMinMaxYears(): SimpleIntRange
    fun getCountries(): List<String>
}