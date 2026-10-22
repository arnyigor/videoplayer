package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.domain.playback.testFilm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayStartResolverTest {

    private fun series(): Movie = Movie(
        dbId = 5,
        movieId = 5,
        title = "Сериал",
        type = MovieType.SERIAL,
        info = MovieInfo(),
        seasons = listOf(
            // Сезоны и серии намеренно не по порядку: экран деталей сортирует их сам.
            SerialSeason(
                id = 2,
                episodes = listOf(
                    SerialEpisode(id = 22, episode = "2", hls = "https://cdn.test/s2e2.m3u8"),
                    SerialEpisode(id = 21, episode = "1", hls = "https://cdn.test/s2e1.m3u8"),
                )
            ),
            SerialSeason(
                id = 1,
                episodes = listOf(
                    SerialEpisode(
                        id = 11,
                        episode = "1",
                        hls = "https://cdn.test/s1e1.m3u8",
                        dash = "https://cdn.test/s1e1.mpd",
                    ),
                    SerialEpisode(id = 12, episode = "2", hls = ""),
                )
            ),
        ),
    )

    @Test
    fun `no movie reports failure instead of silence`() {
        val start = PlayStartResolver.resolve(movie = null)
        assertEquals(PlayStart.Failed(PlayStartFailure.NO_MOVIE), start)
    }

    @Test
    fun `film without urls reports no source`() {
        val movie = testFilm(urls = emptyList())
        assertEquals(
            PlayStart.Failed(PlayStartFailure.NO_SOURCE),
            PlayStartResolver.resolve(movie)
        )
    }

    @Test
    fun `film prefers hd sources and keeps order`() {
        val movie = testFilm(
            urls = emptyList(),
        ).copy(
            cinemaUrlData = CinemaUrlData(
                hdUrl = AnwapUrl(urls = listOf("https://cdn.test/hd.m3u8")),
                cinemaUrl = AnwapUrl(urls = listOf("https://cdn.test/sd.mp4")),
            )
        )

        val sources = PlayStartResolver.cinemaSources(movie)
        assertEquals(
            listOf(
                PlaySource(PlayStartResolver.QUALITY_HD, "https://cdn.test/hd.m3u8"),
                PlaySource(PlayStartResolver.QUALITY_SD, "https://cdn.test/sd.mp4"),
            ),
            sources
        )
        assertEquals(
            "https://cdn.test/hd.m3u8",
            (PlayStartResolver.resolve(movie) as PlayStart.Ready).source.url
        )
    }

    @Test
    fun `blank urls are ignored`() {
        val movie = testFilm(urls = emptyList()).copy(
            cinemaUrlData = CinemaUrlData(
                hdUrl = AnwapUrl(urls = listOf("", "  ")),
                cinemaUrl = AnwapUrl(urls = listOf("https://cdn.test/sd.mp4")),
            )
        )
        val sources = PlayStartResolver.cinemaSources(movie)
        assertEquals(1, sources.size)
        assertEquals("https://cdn.test/sd.mp4", sources.single().url)
    }

    @Test
    fun `serial episode is picked by sorted position not declaration order`() {
        val movie = series()
        // Позиция 0 первого сезона — серия с номером 1, хотя в списке она вторая.
        val firstSeasonFirstEpisode = PlayStartResolver.sources(movie, 0, 0)
        assertTrue(firstSeasonFirstEpisode.any { it.url == "https://cdn.test/s1e1.m3u8" })

        val secondSeasonFirstEpisode = PlayStartResolver.sources(movie, 1, 0)
        assertEquals(
            listOf("https://cdn.test/s2e1.m3u8"),
            secondSeasonFirstEpisode.map { it.url }
        )
    }

    @Test
    fun `serial episode without links reports no source`() {
        val movie = series()
        assertEquals(
            PlayStart.Failed(PlayStartFailure.NO_SOURCE),
            PlayStartResolver.resolve(movie, seasonPosition = 0, episodePosition = 1)
        )
    }

    @Test
    fun `selected index out of range falls back to the first source`() {
        val movie = testFilm(urls = listOf("https://cdn.test/a.m3u8"))
        val start = PlayStartResolver.resolve(movie, selectedIndex = 7)
        assertEquals("https://cdn.test/a.m3u8", (start as PlayStart.Ready).source.url)
    }

    @Test
    fun `episode numbers are parsed for ordering`() {
        assertEquals(12, PlayStartResolver.episodeNumber(SerialEpisode(episode = "12")))
        assertEquals(0, PlayStartResolver.episodeNumber(SerialEpisode(episode = "special")))
    }
}
