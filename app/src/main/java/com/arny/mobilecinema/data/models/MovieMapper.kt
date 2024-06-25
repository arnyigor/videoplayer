package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.utils.fromJsonToList
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Mapper
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.domain.models.UrlData
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import javax.inject.Inject

class MovieMapper @Inject constructor() : Mapper<MovieEntity, Movie> {
    override fun transform(data: MovieEntity): Movie {
        val seasons = if (data.seasons.isNotBlank()) {
            data.seasons.fromJsonToList<SerialSeason>(initGson())
        } else {
            emptyList()
        }
        return Movie(
            dbId = data.dbId,
            movieId = data.movieId,
            title = data.title,
            pageUrl = data.pageUrl,
            type = MovieType.fromValue(data.type),
            img = data.img,
            info = MovieInfo(
                year = data.year,
                quality = data.quality,
                translate = data.translate,
                durationSec = data.durationSec,
                age = data.age,
                countries = data.countries.split(","),
                genres = data.genre.split(","),
                likes = data.likes,
                dislikes = data.dislikes,
                ratingImdb = data.ratingImdb,
                ratingKP = data.ratingKp,
                directors = data.directors.split(","),
                actors = data.actors.split(","),
                description = data.description,
                updated = data.updated,
                origTitle = data.origTitle
            ),
            seasons = seasons,
            customData = data.customData,
            cinemaUrlData = CinemaUrlData(
                hdUrl = UrlData(
                    urls = data.hdUrls.split(","),
                    poster = data.hdUrlsPoster
                ),
                cinemaUrl = UrlData(
                    urls = data.cinemaUrls.split(","),
                    poster = data.cinemaUrlsPoster
                ),
                trailerUrl = UrlData(
                    urls = data.trailerUrls.split(","),
                    poster = data.trailerUrlsPoster
                )
            ),
        )
    }

    private fun initGson() = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(
            ArrayList::class.java,
            JsonDeserializer { json, _, _ -> readSeasons(json) }
        )
        .create()
}
