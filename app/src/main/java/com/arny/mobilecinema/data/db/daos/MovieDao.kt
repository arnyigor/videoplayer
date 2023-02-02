package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.domain.models.ViewMovie

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies ORDER BY updated DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, updated FROM movies")
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?
}