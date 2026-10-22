package com.arny.mobilecinema.presentation.history

import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryRowMapperTest {

    private fun movie(type: MovieType = MovieType.SERIAL) = ViewMovie(
        dbId = 7,
        title = "Колония",
        type = type.value,
        img = "/serials/7.jpg",
        year = 2016,
    )

    private fun progress(
        positionMs: Long = 600_000,
        durationMs: Long? = 2_880_000,
        season: Int? = 2,
        episode: Int? = 5,
        completed: Boolean = false,
        updatedAt: Long = 1_700_000_000_000,
    ) = PlaybackProgress(
        playableId = "ep:7:2:5",
        movieDbId = 7,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
        updatedAt = updatedAt,
        seasonNumber = season,
        episodeNumber = episode,
    )

    @Test
    fun `series row shows season and episode`() {
        val row = HistoryRowMapper.map(movie(), progress())
        assertEquals("S02E05", row.episodeLabel)
        assertEquals(HistoryProgressState.IN_PROGRESS, row.state)
        assertEquals(38, row.remainingMinutes)
        assertEquals(1_700_000_000_000, row.lastWatchedAt)
    }

    @Test
    fun `row without progress is not started and has no fake episode`() {
        val row = HistoryRowMapper.map(movie(), null)
        assertEquals(HistoryProgressState.NOT_STARTED, row.state)
        assertNull(row.episodeLabel)
        assertNull(row.progressFraction)
        assertNull(row.remainingMinutes)
        assertEquals(0L, row.lastWatchedAt)
    }

    @Test
    fun `completed row has full bar and no remaining time`() {
        val row = HistoryRowMapper.map(movie(), progress(completed = true))
        assertEquals(HistoryProgressState.COMPLETED, row.state)
        assertEquals(1f, row.progressFraction!!, 0.001f)
        assertNull(row.remainingMinutes)
    }

    @Test
    fun `unknown duration keeps progress silent`() {
        val row = HistoryRowMapper.map(movie(), progress(durationMs = null))
        assertNull(row.progressFraction)
        assertNull(row.remainingMinutes)
        assertEquals(HistoryProgressState.IN_PROGRESS, row.state)
    }

    @Test
    fun `film row never invents an episode label`() {
        val row = HistoryRowMapper.map(
            movie(MovieType.CINEMA),
            progress(season = null, episode = null)
        )
        assertTrue(!row.isSerial)
        assertNull(row.episodeLabel)
    }

    @Test
    fun `position is clamped to duration`() {
        val row = HistoryRowMapper.map(
            movie(),
            progress(positionMs = 5_000_000, durationMs = 2_880_000)
        )
        assertEquals(1f, row.progressFraction!!, 0.001f)
        assertNull(row.remainingMinutes)
    }

    @Test
    fun `status labels distinguish completed progress and not started`() {
        assertEquals(
            com.arny.mobilecinema.R.string.history_status_completed,
            HistoryRowMapper.statusLabelRes(HistoryRowMapper.map(movie(), progress(completed = true)))
        )
        assertEquals(
            com.arny.mobilecinema.R.string.history_status_not_started,
            HistoryRowMapper.statusLabelRes(HistoryRowMapper.map(movie(), null))
        )
        assertEquals(
            com.arny.mobilecinema.R.string.mobile_remaining_minutes,
            HistoryRowMapper.statusLabelRes(HistoryRowMapper.map(movie(), progress()))
        )
    }
}
