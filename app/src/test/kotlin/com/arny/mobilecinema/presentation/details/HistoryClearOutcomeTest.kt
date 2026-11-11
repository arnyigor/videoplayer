package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регресс на баг «удалил из истории — вернуло на карточку»:
 * после удаления должно быть ровно одно сообщение и не более одного выхода назад.
 */
class HistoryClearOutcomeTest {

    @Test
    fun `cinema history removal reports success and leaves the screen`() {
        val outcome = historyClearOutcome(MovieType.CINEMA, total = true)!!
        assertEquals(R.string.movie_viewhistory_cleared, outcome.messageRes)
        assertTrue(outcome.navigateBack)
    }

    @Test
    fun `cinema cache-only removal stays on the card`() {
        val outcome = historyClearOutcome(MovieType.CINEMA, total = false)!!
        assertEquals(R.string.movie_cache_cleared, outcome.messageRes)
        assertFalse(outcome.navigateBack)
    }

    @Test
    fun `serial history removal reports success and leaves the screen`() {
        val outcome = historyClearOutcome(MovieType.SERIAL, total = true)!!
        assertEquals(R.string.serial_history_cleared, outcome.messageRes)
        assertTrue(outcome.navigateBack)
    }

    @Test
    fun `serial episode cache removal stays on the card`() {
        val outcome = historyClearOutcome(MovieType.SERIAL, total = false)!!
        assertEquals(R.string.serial_episode_cache_cleared, outcome.messageRes)
        assertFalse(outcome.navigateBack)
    }

    @Test
    fun `unknown type produces no action at all`() {
        assertNull(historyClearOutcome(null, total = true))
        assertNull(historyClearOutcome(MovieType.NO_TYPE, total = true))
    }
}
