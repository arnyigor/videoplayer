package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieMinimal

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @Query("SELECT * FROM movies ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(limit: Int, offset: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE title LIKE :title")
    suspend fun findByTitle(title: String): List<MovieEntity>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, updated FROM movies")
    fun getAllMinimal(): List<MovieMinimal>
}