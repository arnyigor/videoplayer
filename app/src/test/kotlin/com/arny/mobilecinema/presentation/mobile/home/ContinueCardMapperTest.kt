package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.domain.playback.ContinueWatchingItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContinueCardMapperTest {

    private fun item(
        positionMs: Long = 600_000,
        durationMs: Long? = 2_880_000,
        season: Int? = 2,
        episode: Int? = 5,
    ) = ContinueWatchingItem(
        movieDbId = 7,
        title = "Колония",
        posterUrl = "/serials/7.jpg",
        playableId = "ep:7:2:5",
        positionMs = positionMs,
        durationMs = durationMs,
        seasonNumber = season,
        episodeNumber = episode,
        updatedAt = 1L,
    )

    @Test
    fun `series card shows season and episode label`() {
        assertEquals("S02E05", ContinueCardMapper.episodeLabel(item()))
    }

    @Test
    fun `film card has no episode label`() {
        assertNull(ContinueCardMapper.episodeLabel(item(season = null, episode = null)))
    }

    @Test
    fun `fraction is clamped and null without duration`() {
        assertEquals(0.2f, ContinueCardMapper.fraction(item(positionMs = 576_000, durationMs = 2_880_000))!!, 0.01f)
        assertNull(ContinueCardMapper.fraction(item(durationMs = null)))
        assertNull(ContinueCardMapper.fraction(item(durationMs = 0L)))
        assertEquals(1f, ContinueCardMapper.fraction(item(positionMs = 5_000_000, durationMs = 2_880_000))!!, 0.001f)
    }

    @Test
    fun `remaining minutes round up`() {
        assertEquals(38, ContinueCardMapper.remaining(item(positionMs = 600_000, durationMs = 2_880_000))?.minutes)
        assertEquals(1, ContinueCardMapper.remaining(item(positionMs = 2_879_000, durationMs = 2_880_000))?.minutes)
    }

    @Test
    fun `no remaining label at the end or without duration`() {
        assertNull(ContinueCardMapper.remaining(item(positionMs = 2_880_000, durationMs = 2_880_000)))
        assertNull(ContinueCardMapper.remaining(item(durationMs = null)))
    }

    @Test
    fun `card keeps identity for navigation`() {
        val card = ContinueCardMapper.toCard(item())
        assertEquals(7L, card.movieDbId)
        assertEquals("Колония", card.title)
        assertTrue(card.remaining != null)
    }
}
