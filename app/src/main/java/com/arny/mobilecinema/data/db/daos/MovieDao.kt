package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies")
    fun getAll(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE dbId IN (:ids)")
    fun loadAllByIds(ids: IntArray): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE title LIKE :title LIMIT 1 ")
    fun findByTitle(title: String): MovieEntity?

    @Insert
    fun insertAll(vararg movies: MovieEntity)

    @Delete
    fun delete(movieEntity: MovieEntity)
}