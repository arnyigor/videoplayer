package com.arny.mobilecinema.presentation.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalPlayerSourceTest {
    @Test
    fun `detects adaptive streams ignoring query and fragment`() {
        assertEquals("application/x-mpegURL", externalVideoMimeType("https://example.org/MASTER.M3U8?token=123#video"))
        assertEquals("application/dash+xml", externalVideoMimeType("https://example.org/stream.mpd?type=mp4"))
    }

    @Test
    fun `detects direct videos and extensionless URLs`() {
        assertEquals("video/mp4", externalVideoMimeType("https://example.org/movie.mp4"))
        assertEquals("video/mp4", externalVideoMimeType("https://example.org/movie.m4v"))
        assertEquals("video/webm", externalVideoMimeType("https://example.org/movie.webm"))
        assertEquals("video/*", externalVideoMimeType("https://example.org/watch?id=1"))
        assertEquals("video/mp4", externalVideoMimeType("http://example.org/movie.mp4"))
    }

    @Test
    fun `rejects private local and malformed sources`() {
        listOf("", "/movie.mp4", "file:///private/movie.mp4", "content://provider/video/1",
            "intent://example.org/video", "https:///movie.mp4", "not a URL").forEach {
            assertNull(externalVideoMimeType(it), it)
        }
    }

    @Test
    fun `rejects sources that require a site referer header`() {
        assertNull(externalVideoMimeType("https://cdnr.interkh.com/05_26/11/10/N6AQITB3/manifest.mpd"))
        assertNull(externalVideoMimeType("https://x-bc.interkh.com/05_26/11/10/N6AQITB3/index.m3u8"))
        assertNull(externalVideoMimeType("https://api.ortified.ws/video/movie.mp4"))
    }

    @Test
    fun `referer mapping is shared with the built-in player`() {
        assertEquals("https://api.ortified.ws/", requiredVideoReferer("https://cdnr.interkh.com/a/b.m3u8"))
        assertEquals("https://mm.anwap.media/", requiredVideoReferer("https://api.ortified.ws/a.mp4"))
        assertNull(requiredVideoReferer("https://o11.anwap.be/ohls/a.m3u8"))
    }

    @Test
    fun `plain anwap hosts stay externally playable`() {
        assertTrue(externalVideoMimeType("https://o11.anwap.be/ohls/736mBe/index.m3u8") != null)
        assertTrue(requiredVideoReferer("https://o11.anwap.be/ohls/x.m3u8") == null)
        assertFalse(externalVideoMimeType("https://o11.anwap.be/ohls/x.m3u8").isNullOrBlank())
    }

    @Test
    fun `adaptive streams also offer the generic video type`() {
        assertEquals(listOf("application/x-mpegURL", "video/*"), externalMimeCandidates("https://cdn/hls/index.m3u8"))
        assertEquals(listOf("application/dash+xml", "video/*"), externalMimeCandidates("https://cdn/dash/manifest.mpd"))
        assertEquals(listOf("video/mp4", "video/*"), externalMimeCandidates("https://cdn/movie.mp4"))
        assertEquals(listOf("video/*"), externalMimeCandidates("https://cdn/watch?id=1"))
    }

    @Test
    fun `needs-headers sources offer no candidate at all`() {
        assertTrue(externalMimeCandidates("https://api.ortified.ws/video.mp4").isEmpty())
        assertTrue(externalMimeCandidates("").isEmpty())
    }
}
