package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arny.mobilecinema.domain.models.MovieType

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) var dbId: Long = 0,
    @ColumnInfo(name = "movie_id") val movieId: Int = 0,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "origTitle") val origTitle: String = "",
    @ColumnInfo(name = "type") val type: Int = MovieType.NO_TYPE.value,
    @ColumnInfo(name = "pageUrl") val pageUrl: String = "",
    @ColumnInfo(name = "img") val img: String = "",
    @ColumnInfo(name = "year") val year: Int = 0,
    @ColumnInfo(name = "quality") val quality: String = "",
    @ColumnInfo(name = "translate") val translate: String = "",
    @ColumnInfo(name = "durationSec") val durationSec: Int = 0,
    @ColumnInfo(name = "age") val age: Int = 0,
    @ColumnInfo(name = "countries") val countries: String = "",
    @ColumnInfo(name = "genre") val genre: String = "",
    @ColumnInfo(name = "likes") val likes: Int = 0,
    @ColumnInfo(name = "dislikes") val dislikes: Int = 0,
    @ColumnInfo(name = "ratingImdb") val ratingImdb: Double = 0.0,
    @ColumnInfo(name = "ratingKp") val ratingKp: Double = 0.0,
    @ColumnInfo(name = "directors") val directors: String = "",
    @ColumnInfo(name = "actors") val actors: String = "",
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "updated") val updated: Long = 0L,
    @ColumnInfo(name = "seasons") val seasons: String = "",
    @ColumnInfo(name = "hdUrls") val hdUrls: String = "",
    @ColumnInfo(name = "hdUrlsPoster") val hdUrlsPoster: String = "",
    @ColumnInfo(name = "cinemaUrls") val cinemaUrls: String = "",
    @ColumnInfo(name = "cinemaUrlsPoster") val cinemaUrlsPoster: String = "",
    @ColumnInfo(name = "trailerUrls") val trailerUrls: String = "",
    @ColumnInfo(name = "trailerUrlsPoster") val trailerUrlsPoster: String = "",
)