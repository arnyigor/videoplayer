package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.HistoryEntity

@Dao
interface HistoryDao : BaseDao<HistoryEntity> {
    @Query("SELECT Count(*) FROM history WHERE movie_dbid !=0 AND position != 0 AND movie_dbid IS NOT NULL AND position IS NOT NULL")
    fun getHistoryCount(): Int

    @Query("SELECT * FROM history WHERE movie_dbid =:movieDbId")
    fun getHistory(movieDbId: Long?): HistoryEntity?

    @Query("UPDATE history SET position=:position, season=:season, episode=:episode, latest_time=:currentTimeMs WHERE movie_dbid =:movieDbId")
    fun updateHistory(
        movieDbId: Long?,
        position: Long = 0,
        season: Int = 0,
        episode: Int = 0,
        currentTimeMs: Long = 0L
    ): Int

    @Query("DELETE FROM history WHERE movie_dbid =:movieDbId")
    fun deleteHistory(movieDbId: Long?): Int

    @Query("DELETE FROM history")
    fun deleteAllHistory(): Int
}