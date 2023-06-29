package com.arny.mobilecinema.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arny.mobilecinema.domain.models.MovieType

@Entity(
    tableName = "movies",
    indices = [Index(value = ["title","pageUrl"], unique = true)]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    @ColumnInfo(name = "movie_id") var movieId: Int = 0,
    @ColumnInfo(name = "title") var title: String = "",
    @ColumnInfo(name = "origTitle") var origTitle: String = "",
    @ColumnInfo(name = "type") var type: Int = MovieType.NO_TYPE.value,
    @ColumnInfo(name = "pageUrl") var pageUrl: String = "",
    @ColumnInfo(name = "img") var img: String = "",
    @ColumnInfo(name = "year") var year: Int = 0,
    @ColumnInfo(name = "quality") var quality: String = "",
    @ColumnInfo(name = "translate") var translate: String = "",
    @ColumnInfo(name = "durationSec") var durationSec: Int = 0,
    @ColumnInfo(name = "age") var age: Int = 0,
    @ColumnInfo(name = "countries") var countries: String = "",
    @ColumnInfo(name = "genre") var genre: String = "",
    @ColumnInfo(name = "likes") var likes: Int = 0,
    @ColumnInfo(name = "dislikes") var dislikes: Int = 0,
    @ColumnInfo(name = "ratingImdb") var ratingImdb: Double = 0.0,
    @ColumnInfo(name = "ratingKp") var ratingKp: Double = 0.0,
    @ColumnInfo(name = "directors") var directors: String = "",
    @ColumnInfo(name = "actors") var actors: String = "",
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "updated") var updated: Long = 0L,
    @ColumnInfo(name = "seasons") var seasons: String = "",
    @ColumnInfo(name = "hdUrls") var hdUrls: String = "",
    @ColumnInfo(name = "hdUrlsPoster") var hdUrlsPoster: String = "",
    @ColumnInfo(name = "cinemaUrls") var cinemaUrls: String = "",
    @ColumnInfo(name = "cinemaUrlsPoster") var cinemaUrlsPoster: String = "",
    @ColumnInfo(name = "trailerUrls") var trailerUrls: String = "",
    @ColumnInfo(name = "trailerUrlsPoster") var trailerUrlsPoster: String = "",
    @ColumnInfo(name = "addedToHistory") var addedToHistory: Long = 0L,
) {
    fun clear() {
        movieId = 0
        title = ""
        origTitle = ""
        type = MovieType.NO_TYPE.value
        pageUrl = ""
        img = ""
        year = 0
        quality = ""
        translate = ""
        durationSec = 0
        age = 0
        countries = ""
        genre = ""
        likes = 0
        dislikes = 0
        ratingImdb = 0.0
        ratingKp = 0.0
        directors = ""
        actors = ""
        description = ""
        updated = 0L
        seasons = ""
        hdUrls = ""
        hdUrlsPoster = ""
        cinemaUrls = ""
        cinemaUrlsPoster = ""
        trailerUrls = ""
        trailerUrlsPoster = ""
        addedToHistory = 0L
    }
}