package com.arny.mobilecinema.presentation.services

import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.MoviesData
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.domain.models.UrlData
import com.google.gson.stream.JsonReader

private fun readMovieData(reader: JsonReader): MoviesData {
    reader.beginObject()
    var movies: List<Movie> = emptyList()
    while (reader.hasNext()) {
        val name = reader.nextName()
        if (name == "movies") {
            movies = readMovies(reader)
        }
    }
    reader.endObject()
    return MoviesData(movies)
}

private fun readMovies(reader: JsonReader): List<Movie> {
    val movies: MutableList<Movie> = ArrayList(10000)
    reader.beginArray()
    while (reader.hasNext()) {
        movies.add(readMovie(reader))
    }
    reader.endArray()
    return movies
}

private fun readMovie(reader: JsonReader): Movie {
    var movieId: Int = -1
    var title: String? = null
    var origTitle = ""
    var type: MovieType = MovieType.NO_TYPE
    var pageUrl = ""
    var img = ""
    var info = MovieInfo()
    var seasons: List<SerialSeason> = emptyList()
    var cinemaUrlData: CinemaUrlData? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "movieId" -> {
                movieId = reader.nextInt()
            }

            "title" -> {
                title = reader.nextString()
            }

            "origTitle" -> {
                origTitle = reader.nextString()
            }

            "type" -> {
                type = MovieType.fromValue(reader.nextString())
            }

            "pageUrl" -> {
                pageUrl = reader.nextString()
            }

            "img" -> {
                img = reader.nextString()
            }

            "info" -> {
                info = readInfo(reader)
            }

            "seasons" -> {
                seasons = readSeasons(reader)
            }

            "cinemaUrlData" -> {
                cinemaUrlData = readCinemaUrlData(reader)
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return Movie(
        movieId = movieId,
        title = title.orEmpty(),
        origTitle = origTitle,
        type = type,
        pageUrl = pageUrl,
        img = img,
        info = info,
        seasons = seasons,
        cinemaUrlData = cinemaUrlData,
    )
}

private fun readCinemaUrlData(reader: JsonReader): CinemaUrlData? {
    var hdUrl: UrlData? = null
    var cinemaUrl: UrlData? = null
    var trailerUrl: UrlData? = null

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "hdUrl" -> {
                hdUrl = readUrlData(reader)
            }

            "cinemaUrl" -> {
                cinemaUrl = readUrlData(reader)
            }

            "trailerUrl" -> {
                trailerUrl = readUrlData(reader)
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return CinemaUrlData(
        hdUrl = hdUrl,
        cinemaUrl = cinemaUrl,
        trailerUrl = trailerUrl
    )
}

private fun readUrlData(reader: JsonReader): UrlData {
    var duration: String? = null
    var file: String? = null
    var poster: String? = null
    var url: String? = null
    var urls: List<String> = emptyList()

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "duration" -> {
                duration = reader.nextString()
            }

            "file" -> {
                file = reader.nextString()
            }

            "poster" -> {
                poster = reader.nextString()
            }

            "url" -> {
                url = reader.nextString()
            }

            "urls" -> {
                urls = readStringArray(reader)
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return UrlData(
        duration = duration,
        file = file,
        poster = poster,
        url = url,
        urls = urls
    )
}

private fun readInfo(reader: JsonReader): MovieInfo {
    var year = 0
    var quality = ""
    var translate = ""
    var durationSec = 0
    var age: Int = -1
    var countries: List<String> = emptyList()
    var genres: List<String> = emptyList()
    var likes = 0
    var dislikes = 0
    var ratingImdb: Double = -1.0
    var ratingKP: Double = -1.0
    var directors: List<String> = emptyList()
    var actors: List<String> = emptyList()
    var description = ""
    var updated = 0L
    var origTitle = ""

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "year" -> {
                year = reader.nextInt()
            }

            "quality" -> {
                quality = reader.nextString()
            }

            "translate" -> {
                translate = reader.nextString()
            }

            "durationSec" -> {
                durationSec = reader.nextInt()
            }

            "age" -> {
                age = reader.nextInt()
            }

            "countries" -> {
                countries = readStringArray(reader)
            }

            "genres" -> {
                genres = readStringArray(reader)
            }

            "likes" -> {
                likes = reader.nextInt()
            }

            "dislikes" -> {
                dislikes = reader.nextInt()
            }

            "ratingImdb" -> {
                ratingImdb = reader.nextDouble()
            }

            "ratingKP" -> {
                ratingKP = reader.nextDouble()
            }

            "directors" -> {
                directors = readStringArray(reader)
            }

            "actors" -> {
                actors = readStringArray(reader)
            }

            "description" -> {
                description = reader.nextString()
            }

            "updated" -> {
                updated = reader.nextLong()
            }

            "origTitle" -> {
                origTitle = reader.nextString()
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return MovieInfo(
        year = year,
        quality = quality,
        translate = translate,
        durationSec = durationSec,
        age = age,
        countries = countries,
        genres = genres,
        likes = likes,
        dislikes = dislikes,
        ratingImdb = ratingImdb,
        ratingKP = ratingKP,
        directors = directors,
        actors = actors,
        description = description,
        updated = updated,
        origTitle = origTitle
    )
}

private fun readSeasons(reader: JsonReader): List<SerialSeason> {
    val list = mutableListOf<SerialSeason>()
    reader.beginArray()
    while (reader.hasNext()) {
        list.add(readSeason(reader))
    }
    reader.endArray()
    return list
}

private fun readSeason(reader: JsonReader): SerialSeason {
    var id: Int = -1
    var episodes: List<SerialEpisode> = emptyList()
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "id" -> {
                id = reader.nextInt()
            }

            "episodes" -> {
                episodes = readEpisodes(reader)
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return SerialSeason(
        id = id,
        episodes = episodes
    )
}

private fun readEpisodes(reader: JsonReader): List<SerialEpisode> {
    val list = mutableListOf<SerialEpisode>()
    reader.beginArray()
    while (reader.hasNext()) {
        list.add(readEpisode(reader))
    }
    reader.endArray()
    return list
}

private fun readEpisode(reader: JsonReader): SerialEpisode {
    var id = -1
    var episode = ""
    var title = ""
    var hls = ""
    var dash = ""
    var poster = ""

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "id" -> {
                id = reader.nextInt()
            }

            "episode" -> {
                episode = reader.nextString()
            }

            "title" -> {
                title = reader.nextString()
            }

            "hls" -> {
                hls = reader.nextString()
            }

            "dash" -> {
                dash = reader.nextString()
            }

            "poster" -> {
                poster = reader.nextString()
            }

            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return SerialEpisode(
        id = id,
        episode = episode,
        title = title,
        hls = hls,
        dash = dash,
        poster = poster
    )
}

private fun readStringArray(reader: JsonReader): List<String> {
    val list = mutableListOf<String>()
    reader.beginArray()
    while (reader.hasNext()) {
        list.add(reader.nextString())
    }
    reader.endArray()
    return list
}
