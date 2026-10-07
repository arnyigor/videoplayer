package com.arny.mobilecinema.domain.interactors.jsoupupdate

import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdBackfillTest {

    private val now = 1_791_000_000_000L
    private val longAgo = now - 200L * 24 * 60 * 60 * 1000

    private fun film(hd: List<String>, cinema: List<String>, fetchedAt: Long) = Movie(
        type = MovieType.CINEMA,
        info = MovieInfo(detailsFetchedAt = fetchedAt),
        cinemaUrlData = CinemaUrlData(
            hdUrl = AnwapUrl(urls = hd),
            cinemaUrl = AnwapUrl(urls = cinema)
        )
    )

    @Test
    fun `film with only sd and stale details is re-parsed`() {
        val movie = film(hd = emptyList(), cinema = listOf(SD), fetchedAt = longAgo)

        assertTrue(movie.needsHdBackfill(now))
    }

    @Test
    fun `film already parsed recently is not re-parsed twice in a row`() {
        val movie = film(hd = emptyList(), cinema = listOf(SD), fetchedAt = now - 60_000)

        assertFalse(movie.needsHdBackfill(now))
    }

    @Test
    fun `film with hd links is not re-parsed`() {
        val movie = film(hd = listOf(HD), cinema = listOf(SD), fetchedAt = longAgo)

        assertFalse(movie.needsHdBackfill(now))
    }

    @Test
    fun `film without any links is left to hasAllVideoData`() {
        val movie = film(hd = emptyList(), cinema = emptyList(), fetchedAt = longAgo)

        assertFalse(movie.needsHdBackfill(now))
        assertFalse(movie.hasAllVideoData())
    }

    @Test
    fun `serial with episodes missing hd is re-parsed once details are stale`() {
        val serial = Movie(
            type = MovieType.SERIAL,
            info = MovieInfo(detailsFetchedAt = longAgo),
            seasons = listOf(
                SerialSeason(
                    id = 1,
                    episodes = listOf(
                        SerialEpisode(episode = "1", hls = SD, dash = HD),
                        SerialEpisode(episode = "2", hls = SD, dash = "")
                    )
                )
            )
        )

        assertTrue(serial.needsHdBackfill(now))
        assertFalse(serial.copy(info = MovieInfo(detailsFetchedAt = now)).needsHdBackfill(now))
    }

    private companion object {
        const val SD = "https://o3.anwap.be/ohls/x/y.m3u8"
        const val HD = "https://o11.anwap.be/ohls/x/y.mpd"
    }
}
