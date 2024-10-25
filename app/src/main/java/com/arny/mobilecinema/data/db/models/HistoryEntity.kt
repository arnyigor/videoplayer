package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index(value = ["movie_dbid"], unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    @ColumnInfo(name = "movie_dbid") var movieDbId: Long = 0,
    @ColumnInfo(name = "position") var position: Long = 0,
    @ColumnInfo(name = "episode") var episode: Int = 0,
    @ColumnInfo(name = "season") var season: Int = 0,
    @ColumnInfo(name = "latest_time", defaultValue = "0") var latestTime: Long = 0L,
)