package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie

interface MoviesRepository {
    var order: String
    fun getMovies(search: String, order: String): Pager<Int, ViewMovie>
    fun getMovie(id: Long): Movie?
    fun getSaveData(dbId: Long?): SaveData
    fun saveCinemaPosition(dbId: Long?, position: Long)
    fun saveSerialPosition(id: Long?, season: Int, episode: Int, episodePosition: Long)
    fun getHistoryMovies(search: String): Pager<Int, ViewMovie>
    fun clearViewHistory(dbId: Long?): Boolean
    suspend fun isHistoryEmpty(): Boolean
    fun saveOrder(order: String)
    fun clearAllViewHistory():Boolean
}