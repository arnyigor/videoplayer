package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_progress",
    indices = [
        Index(value = ["movie_dbid"]),
        Index(value = ["latest_time"]),
        Index(value = ["movie_dbid", "season", "episode"], unique = true)
    ]
)
data class PlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    @ColumnInfo(name = "movie_dbid") val movieDbId: Long = 0,
    @ColumnInfo(name = "season") val season: Int = 0,
    @ColumnInfo(name = "episode") val episode: Int = 0,
    @ColumnInfo(name = "played_ms", defaultValue = "0") val playedMs: Long = 0L,
    @ColumnInfo(name = "latest_time", defaultValue = "0") val latestTime: Long = 0L,
)
