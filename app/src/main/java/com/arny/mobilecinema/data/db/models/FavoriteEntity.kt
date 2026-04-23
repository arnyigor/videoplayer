package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["movie_dbid"], unique = true),
        Index(value = ["latest_time"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    @ColumnInfo(name = "movie_dbid") var movieDbId: Long = 0,
    @ColumnInfo(name = "latest_time", defaultValue = "0") var latestTime: Long = 0L
)