package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class PersonalizationMovieSignal(
    @Embedded val movie: MovieEntity,
    @ColumnInfo(name = "favoriteLatestTime") val favoriteLatestTime: Long?,
    @ColumnInfo(name = "playbackMs") val playbackMs: Long,
    @ColumnInfo(name = "meaningfulEpisodes") val meaningfulEpisodes: Int,
)
