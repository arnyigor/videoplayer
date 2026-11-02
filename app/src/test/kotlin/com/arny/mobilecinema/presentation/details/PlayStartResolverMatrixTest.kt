package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** S-010: матрица источников и выбор серии/сезона для фильма и сериала. */
class PlayStartResolverMatrixTest {

    private fun film(hd: List<String>, sd: List<String>) = Movie(
        dbId = 1,
        movieId = 1,
        title = "Фильм",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(
            hdUrl = AnwapUrl(urls = hd),
            cinemaUrl = AnwapUrl(urls = sd),
        ),
    )

    private fun series(vararg seasons: Pair<Int, List<Pair<String, String>>>) = Movie(
        dbId = 2,
        movieId = 2,
        title = "Сериал",
        type = MovieType.SERIAL,
        info = MovieInfo(),
        seasons = seasons.map { (seasonId, episodes) ->
            SerialSeason(
                id = seasonId,
                episodes = episodes.mapIndexed { index, (number, url) ->
                    SerialEpisode(id = seasonId * 100 + index, episode = number, hls = url)
                },
            )
        },
    )

    @Test
    fun `single hd source is ready with hd quality`() {
        val result = PlayStartResolver.resolve(film(listOf("https://cdn.test/a.m3u8"), emptyList()))
        assertEquals(
            PlayStart.Ready(PlaySource(PlayStartResolver.QUALITY_HD, "https://cdn.test/a.m3u8")),
            result
        )
    }

    @Test
    fun `multiple sources keep hd first and honour selected index`() {
        val movie = film(
            listOf("https://cdn.test/hd1.m3u8", "https://cdn.test/hd2.m3u8"),
            listOf("https://cdn.test/sd1.m3u8"),
        )
        val first = PlayStartResolver.resolve(movie, selectedIndex = 0)
        val third = PlayStartResolver.resolve(movie, selectedIndex = 2)
        assertEquals(
            PlayStart.Ready(PlaySource(PlayStartResolver.QUALITY_HD, "https://cdn.test/hd1.m3u8")),
            first
        )
        assertEquals(
            PlayStart.Ready(PlaySource(PlayStartResolver.QUALITY_SD, "https://cdn.test/sd1.m3u8")),
            third
        )
    }

    @Test
    fun `index outside range falls back to first source`() {
        val movie = film(listOf("https://cdn.test/hd1.m3u8"), emptyList())
        assertEquals(PlayStartResolver.resolve(movie, selectedIndex = 0),
            PlayStartResolver.resolve(movie, selectedIndex = 7))
    }

    @Test
    fun `blank urls are not sources`() {
        val movie = film(listOf("   ", ""), listOf(""))
        assertEquals(PlayStart.Failed(PlayStartFailure.NO_SOURCE), PlayStartResolver.resolve(movie))
    }

    @Test
    fun `empty cinema url data is no source`() {
        val movie = Movie(
            dbId = 3, movieId = 3, title = "Без ссылок", type = MovieType.CINEMA,
            info = MovieInfo(), cinemaUrlData = null,
        )
        assertEquals(PlayStart.Failed(PlayStartFailure.NO_SOURCE), PlayStartResolver.resolve(movie))
    }

    @Test
    fun `series picks episode of chosen season`() {
        val movie = series(
            1 to listOf("1" to "https://cdn.test/s1e1.m3u8", "2" to "https://cdn.test/s1e2.m3u8"),
            2 to listOf("1" to "https://cdn.test/s2e1.m3u8"),
        )
        val season2 = PlayStartResolver.resolve(movie, seasonPosition = 1, episodePosition = 0)
        // У серии задан только hls (dash пуст), поэтому качество SD — это ожидаемое поведение.
        assertEquals(
            PlayStart.Ready(PlaySource(PlayStartResolver.QUALITY_SD, "https://cdn.test/s2e1.m3u8")),
            season2
        )
    }

    @Test
    fun `series season and episode out of range is no source`() {
        val movie = series(1 to listOf("1" to "https://cdn.test/s1e1.m3u8"))
        assertEquals(
            PlayStart.Failed(PlayStartFailure.NO_SOURCE),
            PlayStartResolver.resolve(movie, seasonPosition = 3, episodePosition = 0)
        )
        assertTrue(PlayStartResolver.episodeAt(movie, 3, 0) == null)
    }

    @Test
    fun `null movie is no movie`() {
        assertEquals(PlayStart.Failed(PlayStartFailure.NO_MOVIE), PlayStartResolver.resolve(null))
    }
}
