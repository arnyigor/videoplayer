package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.ContinueWatchingRow
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(progress: PlaybackProgressEntity): Long

    @Query("SELECT * FROM playback_progress WHERE profile_id = :profileId AND playable_id = :playableId LIMIT 1")
    fun findByPlayable(profileId: String, playableId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profile_id = :profileId AND movie_dbid = :movieDbId")
    fun findAllForMovie(profileId: String, movieDbId: Long): List<PlaybackProgressEntity>

    /**
     * Строка старого формата (до появления playableId) — основа ленивой миграции.
     */
    @Query(
        """
        SELECT * FROM playback_progress
        WHERE profile_id = :profileId
            AND movie_dbid = :movieDbId
            AND season = :season
            AND episode = :episode
            AND (playable_id = '' OR playable_id LIKE 'legacy:%')
        LIMIT 1
        """
    )
    fun findLegacy(profileId: String, movieDbId: Long, season: Int, episode: Int): PlaybackProgressEntity?

    @Query(
        """
        UPDATE playback_progress
        SET playable_id = :playableId,
            season = :season,
            episode = :episode,
            season_number = :seasonNumber,
            episode_number = :episodeNumber,
            position_ms = :positionMs,
            duration_ms = :durationMs,
            played_ms = :playedMs,
            latest_time = :latestTime,
            completed = :completed,
            updated_at = :updatedAt
        WHERE dbId = :dbId
        """
    )
    fun updateProgress(
        dbId: Long,
        playableId: String,
        season: Int,
        episode: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long,
        playedMs: Long,
        latestTime: Long,
        completed: Boolean,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT p.*, m.title AS title, m.img AS img, m.type AS type, m.year AS year
        FROM playback_progress p
        JOIN movies m ON m.dbId = p.movie_dbid
        WHERE p.profile_id = :profileId
            AND p.completed = 0
            AND p.position_ms > :minPositionMs
            AND p.updated_at = (
                SELECT MAX(p2.updated_at)
                FROM playback_progress p2
                WHERE p2.movie_dbid = p.movie_dbid
                    AND p2.profile_id = :profileId
                    AND p2.completed = 0
            )
        ORDER BY p.updated_at DESC
        LIMIT :limit
        """
    )
    fun observeContinueWatching(
        profileId: String,
        minPositionMs: Long,
        limit: Int,
    ): Flow<List<ContinueWatchingRow>>

    @Query("DELETE FROM playback_progress WHERE movie_dbid = :movieDbId")
    fun deleteForMovie(movieDbId: Long?): Int

    @Query("DELETE FROM playback_progress")
    fun deleteAll(): Int
}
