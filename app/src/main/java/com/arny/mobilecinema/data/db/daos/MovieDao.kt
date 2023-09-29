package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.domain.models.SimpleIntRange
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao : BaseDao<MovieEntity> {

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, title, updated, genre FROM movies")
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?

    @Query("SELECT dbId FROM movies ORDER BY dbId DESC LIMIT 1")
    fun getLastId(): Long

    @Query("SELECT DISTINCT genre FROM movies")
    fun allGenres(): List<String>

    @Query("SELECT MIN(year) as `from`, MAX(year) as `to` FROM movies WHERE year > 1900")
    fun getYearsMinMax(): SimpleIntRange
}