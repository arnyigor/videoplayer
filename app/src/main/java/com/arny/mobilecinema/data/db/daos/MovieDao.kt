package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieMinimal

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @Query("SELECT * FROM movies")
    fun getAll(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE dbId IN (:ids)")
    fun loadAllByIds(ids: IntArray): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE title LIKE :title")
    fun findByTitle(title: String): List<MovieEntity>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, updated FROM movies WHERE pageUrl= :page")
    fun findByPageUrl(page: String): MovieMinimal?

    @Insert
    fun insertAll(vararg movies: MovieEntity)

    @Delete
    fun delete(movieEntity: MovieEntity)
}