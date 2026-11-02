package com.arny.mobilecinema.presentation.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Контракт S-009: адреса в логах только отредактированные, мусорные URL отсекаются заранее. */
class PlayTraceTest {

    @Test
    fun `redact drops query tokens and fragment`() {
        val url = "https://cdn.test/video/ep1.m3u8?token=SECRET&sig=abc#t=10"
        assertEquals("https://cdn.test/video/ep1.m3u8", PlayTrace.redactUrl(url))
    }

    @Test
    fun `redact drops userinfo and port-only authority`() {
        assertEquals(
            "https://cdn.test/path",
            PlayTrace.redactUrl("https://user:pass@cdn.test/path?cookie=SECRET")
        )
    }

    @Test
    fun `redact keeps scheme and host without path`() {
        assertEquals(
            "http://127.0.0.1:8765/test.mp4",
            PlayTrace.redactUrl("http://127.0.0.1:8765/test.mp4?x=1")
        )
    }

    @Test
    fun `redact marks empty and schemeless urls`() {
        assertEquals("<empty>", PlayTrace.redactUrl("   "))
        assertEquals("<no-scheme>", PlayTrace.redactUrl("/films/comm/47737"))
    }

    @Test
    fun `playable url requires absolute http or https with host`() {
        assertTrue(PlayTrace.isPlayableUrl("https://cdn.test/a.m3u8"))
        assertTrue(PlayTrace.isPlayableUrl("http://127.0.0.1:8765/test.mp4"))
        assertFalse(PlayTrace.isPlayableUrl("/films/comm/47737"))
        assertFalse(PlayTrace.isPlayableUrl("ftp://cdn.test/a.mkv"))
        assertFalse(PlayTrace.isPlayableUrl("https://"))
        assertFalse(PlayTrace.isPlayableUrl(""))
    }

    @Test
    fun `correlation ids differ between clicks`() {
        val ids = (1..20).map { PlayTrace.nextId() }
        assertTrue(ids.all { it.startsWith("p") })
        assertTrue(ids.toSet().size > 15, "ids должны различаться, получено: $ids")
    }
}
