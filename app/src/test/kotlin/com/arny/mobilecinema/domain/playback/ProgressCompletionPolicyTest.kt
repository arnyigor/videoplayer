package com.arny.mobilecinema.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressCompletionPolicyTest {

    private val policy = ProgressCompletionPolicy()

    @Test
    fun `two hour film is completed only within five percent of the end`() {
        val duration = 2 * 60 * 60_000L

        assertFalse(policy.isCompleted(positionMs = (duration * 0.94).toLong(), durationMs = duration))
        assertTrue(policy.isCompleted(positionMs = (duration * 0.955).toLong(), durationMs = duration))
    }

    @Test
    fun `short episode uses the thirty second floor`() {
        val duration = 10 * 60_000L

        assertFalse(policy.isCompleted(positionMs = duration - 40_000, durationMs = duration))
        assertTrue(policy.isCompleted(positionMs = duration - 25_000, durationMs = duration))
    }

    @Test
    fun `credits marker wins over percentage`() {
        val duration = 60 * 60_000L
        val withCredits = ProgressCompletionPolicy(creditsStartMs = (duration * 0.8).toLong())

        assertTrue(withCredits.isCompleted(positionMs = (duration * 0.81).toLong(), durationMs = duration))
        assertFalse(withCredits.isCompleted(positionMs = (duration * 0.79).toLong(), durationMs = duration))
    }

    @Test
    fun `unknown duration never completes but persists after ten seconds`() {
        assertFalse(policy.isCompleted(positionMs = 5_000_000, durationMs = null))
        assertFalse(policy.shouldPersist(positionMs = 9_999, durationMs = null))
        assertTrue(policy.shouldPersist(positionMs = 10_001, durationMs = null))
    }

    @Test
    fun `completed and out of range positions are not persisted`() {
        val duration = 45 * 60_000L

        assertFalse(policy.shouldPersist(positionMs = duration - 1_000, durationMs = duration))
        assertFalse(policy.shouldPersist(positionMs = duration + 5_000, durationMs = duration))
        assertTrue(policy.shouldPersist(positionMs = 10 * 60_000, durationMs = duration))
    }

    @Test
    fun `resume position is clamped and resets finished video`() {
        val duration = 45 * 60_000L

        assertEquals(600_000L, policy.resumePosition(positionMs = 600_000, durationMs = duration))
        // позиция за пределами длительности считается завершённой (сменилась версия потока)
        assertEquals(0L, policy.resumePosition(positionMs = duration + 60_000, durationMs = duration))
        assertEquals(0L, policy.resumePosition(positionMs = duration - 1_000, durationMs = duration))
        assertEquals(0L, policy.resumePosition(positionMs = -5_000, durationMs = duration))
        assertEquals(123_000L, policy.resumePosition(positionMs = 123_000, durationMs = null))
    }

    @Test
    fun `fraction is null without a known duration`() {
        assertNull(PlaybackProgress("film:1", 1L, 0).fraction)
        assertEquals(0.5f, PlaybackProgress("film:1", 1L, 500, 1_000).fraction)
    }
}
