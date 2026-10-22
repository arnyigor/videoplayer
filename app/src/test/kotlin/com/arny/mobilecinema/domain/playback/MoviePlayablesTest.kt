package com.arny.mobilecinema.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoviePlayablesTest {

    @Test
    fun `seasons and episodes are ordered by catalog numbers, not array order`() {
        val movie = testSeries(
            listOf(
                testSeason(2, testEpisode(21, "1"), testEpisode(22, "2")),
                testSeason(1, testEpisode(12, "2"), testEpisode(11, "1")),
            )
        )

        val refs = movie.playableRefs()

        assertEquals(
            listOf("ep:42:1:11", "ep:42:1:12", "ep:42:2:21", "ep:42:2:22"),
            refs.map { it.id.value },
        )
        assertEquals(listOf(1, 1, 2, 2), refs.map { it.seasonNumber })
        assertEquals(listOf(1, 2, 1, 2), refs.map { it.episodeNumber })
    }

    @Test
    fun `missing catalog ids fall back to index keys`() {
        val movie = testSeries(
            listOf(
                testSeason(
                    null,
                    testEpisode(0, "1"),
                    testEpisode(0, "2"),
                )
            )
        )

        assertEquals(
            listOf("ep:42:i0:i0", "ep:42:i0:i1"),
            movie.playableRefs().map { it.id.value },
        )
    }

    @Test
    fun `episode without a stream is marked unavailable`() {
        val movie = testSeries(
            listOf(
                testSeason(
                    1,
                    testEpisode(11, "1"),
                    testEpisode(12, "2", hls = ""),
                )
            )
        )

        val refs = movie.playableRefs()

        assertTrue(refs[0].isAvailable)
        assertFalse(refs[1].isAvailable)
        assertNull(refs[1].hlsUrl)
    }

    @Test
    fun `non-numeric episode labels are ordered after numbered ones`() {
        val movie = testSeries(
            listOf(
                testSeason(1, testEpisode(21, "special"), testEpisode(22, "1")),
            )
        )

        assertEquals(listOf(1, null), movie.playableRefs().map { it.episodeNumber })
    }

    @Test
    fun `flat index maps catalog coordinates to the ordered list`() {
        val movie = testSeries(
            listOf(
                testSeason(2, testEpisode(21, "1"), testEpisode(22, "2")),
                testSeason(1, testEpisode(11, "1"), testEpisode(12, "2"), testEpisode(13, "3")),
            )
        )

        assertEquals(0, movie.flatPlayableIndex(0, 0))
        assertEquals(2, movie.flatPlayableIndex(0, 2))
        assertEquals(3, movie.flatPlayableIndex(1, 0))
        assertNull(movie.flatPlayableIndex(0, 5))
        assertNull(movie.flatPlayableIndex(2, 0))
    }

    @Test
    fun `legacy coordinates resolve to the same ordered episodes`() {
        val movie = testSeries(
            listOf(
                testSeason(2, testEpisode(21, "2"), testEpisode(22, "1")),
                testSeason(1, testEpisode(11, "1")),
            )
        )

        assertEquals("ep:42:1:11", movie.legacyPlayableRef(0, 0)?.id?.value)
        assertEquals("ep:42:2:22", movie.legacyPlayableRef(1, 0)?.id?.value)
        assertEquals("ep:42:2:21", movie.legacyPlayableRef(1, 1)?.id?.value)
        assertNull(movie.legacyPlayableRef(1, 5))
    }

    @Test
    fun `film gets duration and availability from catalog`() {
        val refs = testFilm(durationSec = 5400).playableRefs()

        assertEquals(1, refs.size)
        assertEquals("film:42", refs[0].id.value)
        assertEquals(5_400_000L, refs[0].durationMs)
        assertTrue(refs[0].isAvailable)
    }

    @Test
    fun `film without urls is unavailable`() {
        val refs = testFilm(urls = emptyList()).playableRefs()

        assertFalse(refs[0].isAvailable)
    }
}
