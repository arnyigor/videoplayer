package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arny.mobilecinema.domain.playback.PlaybackProgress

@Entity(
    tableName = "playback_progress",
    indices = [
        Index(value = ["movie_dbid"]),
        Index(value = ["latest_time"]),
        Index(value = ["profile_id", "playable_id"], unique = true)
    ]
)
data class PlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    @ColumnInfo(name = "movie_dbid") val movieDbId: Long = 0,
    @ColumnInfo(name = "season") val season: Int = 0,
    @ColumnInfo(name = "episode") val episode: Int = 0,
    @ColumnInfo(name = "played_ms", defaultValue = "0") val playedMs: Long = 0L,
    @ColumnInfo(name = "latest_time", defaultValue = "0") val latestTime: Long = 0L,
    @ColumnInfo(name = "playable_id", defaultValue = "''") val playableId: String = "",
    @ColumnInfo(name = "profile_id", defaultValue = "'default'") val profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
    @ColumnInfo(name = "position_ms", defaultValue = "0") val positionMs: Long = 0L,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long = 0L,
    @ColumnInfo(name = "season_number", defaultValue = "0") val seasonNumber: Int = 0,
    @ColumnInfo(name = "episode_number", defaultValue = "0") val episodeNumber: Int = 0,
    @ColumnInfo(name = "completed", defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L,
) {
    fun toDomain(): PlaybackProgress = PlaybackProgress(
        playableId = playableId,
        movieDbId = movieDbId,
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it > 0L },
        completed = completed,
        updatedAt = updatedAt,
        profileId = profileId,
        seasonNumber = seasonNumber.takeIf { it > 0 },
        episodeNumber = episodeNumber.takeIf { it > 0 },
    )
}
