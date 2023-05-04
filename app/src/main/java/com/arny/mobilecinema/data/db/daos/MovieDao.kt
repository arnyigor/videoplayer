package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.domain.models.ViewMovie

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @RawQuery
    suspend fun getMovies(query: SupportSQLiteQuery): List<ViewMovie>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, title, updated FROM movies")
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?
}