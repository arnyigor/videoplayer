package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.utils.toJson
import com.arny.mobilecinema.domain.models.Movie

fun MovieEntity.setData(data: Movie): MovieEntity {
    movieId = data.movieId
    title = data.title
    type = data.type.value
    pageUrl = data.pageUrl
    img = data.img
    year = data.info.year
    quality = data.info.quality
    translate = data.info.translate
    durationSec = data.info.durationSec
    age = data.info.age
    countries = data.info.countries.joinToString(",")
    genre = data.info.genres.joinToString(",")
    likes = data.info.likes
    dislikes = data.info.dislikes
    ratingImdb = data.info.ratingImdb
    ratingKp = data.info.ratingKP
    directors = data.info.directors.joinToString(",")
    actors = data.info.actors.joinToString(",")
    description = data.info.description
    updated = data.info.updated
    origTitle = data.info.origTitle
    seasons = data.seasons.takeIf { it.isNotEmpty() }?.toJson().orEmpty()
    hdUrls = data.cinemaUrlData?.hdUrl?.urls?.joinToString(",").orEmpty()
    hdUrlsPoster = data.cinemaUrlData?.hdUrl?.poster.orEmpty()
    cinemaUrls = data.cinemaUrlData?.cinemaUrl?.urls?.joinToString(",").orEmpty()
    cinemaUrlsPoster = data.cinemaUrlData?.cinemaUrl?.poster.orEmpty()
    trailerUrls = ""
    trailerUrlsPoster = ""
    return this
}