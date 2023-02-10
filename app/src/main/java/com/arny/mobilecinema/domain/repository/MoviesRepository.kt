package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie

interface MoviesRepository {
    fun getMovies(search: String): Pager<Int, ViewMovie>
    fun getMovie(id: Long): Movie?
    fun getSaveData(dbId: Long?): SaveData
    fun saveCinemaPosition(dbId: Long?, position: Long)
    fun saveSerialPosition(id: Long?, season: Int, episode: Int, episodePosition: Long)
    fun getHistoryMovies(search: String): Pager<Int, ViewMovie>
    fun clearCache(dbId: Long?): Boolean
}