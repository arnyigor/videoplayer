package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(progress: PlaybackProgressEntity): Long

    @Query(
        """
        UPDATE playback_progress
        SET played_ms = played_ms + :playedMs,
            latest_time = :latestTime
        WHERE movie_dbid = :movieDbId
            AND season = :season
            AND episode = :episode
        """
    )
    fun addPlayedMs(
        movieDbId: Long,
        season: Int,
        episode: Int,
        playedMs: Long,
        latestTime: Long
    ): Int

    @Transaction
    fun addProgress(
        movieDbId: Long,
        season: Int,
        episode: Int,
        playedMs: Long,
        latestTime: Long
    ): Boolean {
        if (movieDbId <= 0L || playedMs <= 0L) return false

        val inserted = insert(
            PlaybackProgressEntity(
                movieDbId = movieDbId,
                season = season.coerceAtLeast(0),
                episode = episode.coerceAtLeast(0),
                playedMs = playedMs,
                latestTime = latestTime
            )
        )

        return inserted > 0L || addPlayedMs(
            movieDbId = movieDbId,
            season = season.coerceAtLeast(0),
            episode = episode.coerceAtLeast(0),
            playedMs = playedMs,
            latestTime = latestTime
        ) > 0
    }

    @Query("DELETE FROM playback_progress WHERE movie_dbid = :movieDbId")
    fun deleteForMovie(movieDbId: Long?): Int

    @Query("DELETE FROM playback_progress")
    fun deleteAll(): Int
}
