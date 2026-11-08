package com.arny.mobilecinema.presentation.tv.player

import com.arny.mobilecinema.domain.playback.playableRefs
import com.arny.mobilecinema.domain.playback.testEpisode
import com.arny.mobilecinema.domain.playback.testFilm
import com.arny.mobilecinema.domain.playback.testSeason
import com.arny.mobilecinema.domain.playback.testSeries
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TvPlaybackTargetTest {
    private val series = testSeries(listOf(
        testSeason(1, testEpisode(11, "1"), testEpisode(12, "2")),
        testSeason(2, testEpisode(21, "1")),
    ))

    @Test fun `film retains its saved position`() {
        val film = testFilm()
        val result = resolveTvPlaybackTarget(film, film.playableRefs().single().id.value, 0, 0, 90000)
        assertEquals(90000L, result.positionMs)
        assertEquals(film.playableRefs().single().id, result.ref?.id)
    }

    @Test fun `explicit first episode is not replaced by later resume target`() {
        val first = series.playableRefs().first()
        val result = resolveTvPlaybackTarget(series, first.id.value, 1, 0, 90000)
        assertEquals(first.id, result.ref?.id)
        assertEquals(0L, result.positionMs)
    }

    @Test fun `same episode preserves resume position`() {
        val ref = series.playableRefs().last()
        val result = resolveTvPlaybackTarget(series, ref.id.value, 1, 0, 90000)
        assertEquals(ref.id, result.ref?.id)
        assertEquals(90000L, result.positionMs)
    }

    @Test fun `legacy navigation without playable id preserves resolved resume`() {
        val result = resolveTvPlaybackTarget(series, "", 0, 1, 45000)
        assertEquals(series.playableRefs()[1].id, result.ref?.id)
        assertEquals(45000L, result.positionMs)
    }

    @Test fun `unknown explicit id falls back to current saved episode`() {
        val result = resolveTvPlaybackTarget(series, "ep:missing", 1, 0, 45000)
        assertEquals(series.playableRefs().last().id, result.ref?.id)
        assertEquals(45000L, result.positionMs)
    }

    @Test fun `negative saved position is clamped`() {
        assertEquals(0L, resolveTvPlaybackTarget(testFilm(), "", 0, 0, -1000).positionMs)
    }
}
