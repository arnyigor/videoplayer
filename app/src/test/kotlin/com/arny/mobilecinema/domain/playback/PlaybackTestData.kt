package com.arny.mobilecinema.domain.playback

import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason

internal const val TEST_MOVIE_ID = 42L

internal fun testEpisode(
    id: Int,
    number: String,
    hls: String = "https://cdn.test/$number.m3u8",
    title: String = "Episode $number",
) = SerialEpisode(id = id, episode = number, title = title, hls = hls)

internal fun testSeason(id: Int?, vararg episodes: SerialEpisode) =
    SerialSeason(id = id, episodes = episodes.toList())

internal fun testSeries(
    seasons: List<SerialSeason>,
    movieId: Long = TEST_MOVIE_ID,
) = Movie(
    dbId = movieId,
    movieId = movieId.toInt(),
    title = "Test series",
    type = MovieType.SERIAL,
    seasons = seasons,
    info = MovieInfo(),
)

internal fun testFilm(
    movieId: Long = TEST_MOVIE_ID,
    durationSec: Int = 0,
    urls: List<String> = listOf("https://cdn.test/film.m3u8"),
) = Movie(
    dbId = movieId,
    movieId = movieId.toInt(),
    title = "Test film",
    type = MovieType.CINEMA,
    info = MovieInfo(durationSec = durationSec),
    cinemaUrlData = CinemaUrlData(
        hdUrl = AnwapUrl(urls = urls),
    ),
)

internal fun testProgress(
    playableId: String,
    positionMs: Long,
    durationMs: Long? = 45 * 60_000L,
    completed: Boolean = false,
    updatedAt: Long = 1_000L,
    movieId: Long = TEST_MOVIE_ID,
) = PlaybackProgress(
    playableId = playableId,
    movieDbId = movieId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAt = updatedAt,
)
