package com.arny.mobilecinema.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ContinueWatchingMapperTest {

    @Test
    fun `episode label and fraction are derived from stored values`() {
        val item = ContinueWatchingMapper.map(
            movieDbId = 42,
            title = "Series",
            posterUrl = "https://cdn.test/poster.jpg",
            playableId = "ep:42:2:5",
            positionMs = 19 * 60_000L + 26_000L,
            durationMs = 48 * 60_000L,
            seasonNumber = 2,
            episodeNumber = 5,
            updatedAt = 100,
        )

        assertEquals("S02E05", item.episodeLabel)
        assertEquals("ep:42:2:5", item.playableId)
        assertEquals((19 * 60_000L + 26_000L).toFloat() / (48 * 60_000L), item.progressFraction)
    }

    @Test
    fun `unknown duration hides the progress bar`() {
        val item = ContinueWatchingMapper.map(
            movieDbId = 1,
            title = "Film",
            posterUrl = "",
            playableId = "film:1",
            positionMs = 60_000,
            durationMs = 0,
            seasonNumber = 0,
            episodeNumber = 0,
            updatedAt = 10,
        )

        assertNull(item.progressFraction)
        assertNull(item.episodeLabel)
        assertNull(item.durationMs)
    }

    @Test
    fun `position beyond duration is clamped`() {
        val item = ContinueWatchingMapper.map(
            movieDbId = 1,
            title = "Film",
            posterUrl = "",
            playableId = "film:1",
            positionMs = 9_000,
            durationMs = 5_000,
            seasonNumber = null,
            episodeNumber = null,
            updatedAt = 10,
        )

        assertEquals(1.0f, item.progressFraction)
    }
}
