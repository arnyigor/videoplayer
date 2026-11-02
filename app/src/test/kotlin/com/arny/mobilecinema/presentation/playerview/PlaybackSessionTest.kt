package com.arny.mobilecinema.presentation.playerview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PlaybackSessionTest {
    @Test
    fun `repeated acquisition reuses engine until release`() {
        val released = mutableListOf<Any>()
        val session = PlaybackSession<Any> { released.add(it) }
        val engine = Any()
        var creates = 0
        assertSame(engine, session.acquire { creates++; engine })
        assertSame(engine, session.acquire { creates++; Any() })
        assertEquals(1, creates)
        session.release()
        session.release()
        assertEquals(listOf(engine), released)
        assertNull(session.player)
        val next = Any()
        assertSame(next, session.acquire { next })
    }

    @Test
    fun `failed creation does not retain engine`() {
        val session = PlaybackSession<Any> { }
        try {
            session.acquire { error("creation failed") }
        } catch (_: IllegalStateException) {
            // No engine was acquired.
        }
        assertNull(session.player)
    }
}
