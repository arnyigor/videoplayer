package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.data.utils.fromTypedJson
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Mapper
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialSeason
import javax.inject.Inject

class AnwapMovieMapper @Inject constructor() : Mapper<MovieEntity, AnwapMovie> {
    override fun transform(data: MovieEntity): AnwapMovie {
        return AnwapMovie(
            dbId = data.dbId,
            movieId = data.movieId,
            title = data.title,
            type = MovieType.fromValue(data.type),
            img = data.img,
            info = MovieInfo(
                year = data.year,
                quality = data.quality,
                translate = data.translate,
                durationSec = data.durationSec,
                age = data.age,
                countries = data.countries.split(","),
                genre = data.genre.split(","),
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
            seasons = data.seasons.fromTypedJson<List<SerialSeason>>().orEmpty(),
            cinemaUrlData = CinemaUrlData(
                hdUrl = AnwapUrl(
                    urls = data.hdUrls.split(","),
                    poster = data.hdUrlsPoster
                ),
                cinemaUrl = AnwapUrl(
                    urls = data.cinemaUrls.split(","),
                    poster = data.cinemaUrlsPoster
                ),
                trailerUrl = AnwapUrl(
                    urls = data.trailerUrls.split(","),
                    poster = data.trailerUrlsPoster
                )
            ),
        )
    }
}
