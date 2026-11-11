package com.arny.mobilecinema.presentation.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Regression guard for source labels: the mobile details spinner and the TV source list must
 * show the same text, and the label must never leak query parameters (tokens).
 */
class SourceLabelTest {

    @Test
    fun `label is host and container`() {
        assertEquals("o11.anwap.be • M3U8", shortSourceLabel("https://o11.anwap.be/ohls/abc/index.m3u8"))
        assertEquals("z36.anwap.be • MP4", shortSourceLabel("https://z36.anwap.be/film/movie.mp4"))
        assertEquals("cdnr.interkh.com • MPD", shortSourceLabel("https://cdnr.interkh.com/05_26/a/manifest.mpd"))
    }

    @Test
    fun `query and fragment are stripped from the label`() {
        val label = shortSourceLabel("https://cdn.test/hd.m3u8?token=SECRET&expires=1#frag")
        assertEquals("cdn.test • M3U8", label)
        assertFalse(label.contains("SECRET"), label)
    }

    @Test
    fun `quality prefix is kept`() {
        assertEquals("HD • cdn.test • M3U8", sourceLabel("HD", "https://cdn.test/hd.m3u8"))
        assertEquals("SD • other.test • MP4", sourceLabel("SD", "https://other.test/film.mp4"))
    }

    @Test
    fun `malformed url still produces a usable label`() {
        assertEquals("MP4", shortSourceLabel("movie.mp4"))
        assertEquals("", shortSourceLabel(""))
    }
}
