package com.arny.mobilecinema.presentation.details

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** S-010: двойной тап по «Смотреть» не должен давать два плеера. */
class PlayClickGateTest {

    @Test
    fun `first click is accepted`() {
        assertTrue(PlayClickGate().tryAccept(1_000))
    }

    @Test
    fun `second click inside window is dropped`() {
        val gate = PlayClickGate(windowMillis = 600)
        assertTrue(gate.tryAccept(1_000))
        assertFalse(gate.tryAccept(1_100))
        assertFalse(gate.tryAccept(1_599))
    }

    @Test
    fun `click after window is accepted again`() {
        val gate = PlayClickGate(windowMillis = 600)
        assertTrue(gate.tryAccept(1_000))
        assertTrue(gate.tryAccept(1_600))
    }

    @Test
    fun `reset allows immediate click`() {
        val gate = PlayClickGate(windowMillis = 600)
        assertTrue(gate.tryAccept(1_000))
        gate.reset()
        assertTrue(gate.tryAccept(1_050))
    }

    @Test
    fun `time going backwards does not block forever`() {
        val gate = PlayClickGate(windowMillis = 600)
        assertTrue(gate.tryAccept(10_000))
        // elapsedRealtime монотонен, но защищаемся от отрицательной разницы.
        assertTrue(gate.tryAccept(9_000))
    }
}
