package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.domain.models.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

fun readSeasons(json: JsonElement): List<SerialSeason> {
    val seasonsArr = ArrayList<SerialSeason>(100)
    for (element in json.asJsonArray) {
        val jsonObject = element.asJsonObject
        val id = jsonObject["season"].asInt
        val episodesArr = ArrayList<SerialEpisode>(100)
        for (arr in jsonObject["episodes"].asJsonArray) {
            val obj = arr.asJsonObject
            episodesArr.add(
                SerialEpisode(
                    id = obj["id"].asInt,
                    episode = obj["episode"].asString.orEmpty(),
                    title = obj["title"].asString.orEmpty(),
                    hls = obj["hls"].asString.orEmpty(),
                    dash = obj["dash"].asString.orEmpty(),
                    poster = obj["poster"].asString.orEmpty(),
                )
            )
        }
        seasonsArr.add(SerialSeason(id, episodesArr))
    }
    return seasonsArr
}

fun readMoviesData(data: JsonElement): List<Movie> {
    val obj = data.asJsonObject
    val movies = ArrayList<Movie>(100000)
    val array = obj["movies"].asJsonArray
    for (element in array) {
        movies.add(readMovie(element.asJsonObject))
    }
    movies.trimToSize()
    return movies
}

fun readMovie(obj: JsonObject): Movie {
    val iObj = obj["info"].asJsonObject
    val cJson = obj["cinemaUrlData"].asJsonObject
    val hdUrlJson = cJson["hdUrl"].asJsonObject
    val cUrlJson = cJson["cinemaUrl"].asJsonObject
    val tUrlJson = cJson["trailerUrl"].asJsonObject
    return Movie(
        dbId = obj["dbId"].asLong,
        movieId = obj["movieId"].asInt,
        title = obj["title"].asString.orEmpty(),
        type = MovieType.fromStringValue(obj["type"].asString),
        img = obj["img"].asString.orEmpty(),
        info = MovieInfo(
            year = iObj["year"].asInt,
            quality = iObj["quality"].asString.orEmpty(),
            translate = iObj["translate"].asString.orEmpty(),
            durationSec = iObj["durationSec"].asInt,
            age = iObj["age"].asInt,
            countries = iObj["countries"].asString.orEmpty().split(","),
            genres = iObj["genre"].asString.orEmpty().split(","),
            likes = iObj["likes"].asInt,
            dislikes = iObj["dislikes"].asInt,
            ratingImdb = iObj["ratingImdb"].asDouble,
            ratingKP = iObj["ratingKp"].asDouble,
            directors = iObj["directors"].asString.orEmpty().split(","),
            actors = iObj["actors"].asString.orEmpty().split(","),
            description = iObj["description"].asString.orEmpty(),
            updated = iObj["updated"].asLong,
            origTitle = iObj["origTitle"].asString.orEmpty()
        ),
        seasons = readSeasons(obj["seasons"]),
        cinemaUrlData = CinemaUrlData(
            hdUrl = UrlData(
                urls = hdUrlJson["urls"].asString.orEmpty().split(","),
                poster = hdUrlJson["poster"].asString.orEmpty()
            ),
            cinemaUrl = UrlData(
                urls = cUrlJson["urls"].asString.orEmpty().split(","),
                poster = cUrlJson["poster"].asString.orEmpty()
            ),
            trailerUrl = UrlData(
                urls = tUrlJson["urls"].asString.orEmpty().split(","),
                poster = tUrlJson["poster"].asString.orEmpty()
            )
        )
    )
}
