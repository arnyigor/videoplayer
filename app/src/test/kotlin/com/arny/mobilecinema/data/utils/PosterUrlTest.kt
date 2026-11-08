package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.arny.mobilecinema.presentation.utils.getWithDomain as uiUrl

class PosterUrlTest {
    @Test fun `UI and data layers resolve poster paths identically`() {
        assertEquals("https://example.org/a.jpg", "/a.jpg".uiUrl("https://example.org/films/123"))
        assertEquals("http://cdn.example/a.jpg", "//cdn.example/a.jpg".uiUrl("http://example.org"))
    }

    @Test fun `poster retry only rebases site mirrors not external CDNs`() {
        assertEquals(listOf("https://old.anwap.media/p/a.jpg?x=1&y=2", "https://mm.anwap.media/p/a.jpg?x=1&y=2"),
            posterUrlCandidates("https://old.anwap.media/p/a.jpg?x=1&amp;y=2", "https://mm.anwap.media/films/1"))
        assertEquals(listOf("https://cdn.example/a.jpg"),
            posterUrlCandidates("https://cdn.example/a.jpg", "https://mm.anwap.media"))
        assertEquals(listOf("https://evil-anwap.media/a.jpg"),
            posterUrlCandidates("https://evil-anwap.media/a.jpg", "https://mm.anwap.media"))
    }

    @Test fun `poster candidates support root paths spaces protocols and blank input`() {
        assertEquals(listOf("http://127.0.0.1:8765/poster%20one.png"),
            posterUrlCandidates(" /poster one.png ", "http://127.0.0.1:8765/films/1"))
        assertEquals(listOf("https://cdn.example/a.jpg"),
            posterUrlCandidates("//cdn.example/a.jpg", "https://mm.anwap.media"))
        assertEquals(emptyList<String>(), posterUrlCandidates(null, "https://mm.anwap.media"))
        assertEquals(emptyList<String>(), posterUrlCandidates(" ", "https://mm.anwap.media"))
    }

    @Test fun `remote poster paths never load local files or executable schemes`() {
        for (path in listOf("file:///private/photo.jpg", "content://photos/1", "javascript:alert(1)", "data:image/png;base64,abc")) {
            assertEquals(emptyList<String>(), posterUrlCandidates(path, "https://mm.anwap.media"))
        }
    }

    @Test fun `same-host origin change has one bounded retry`() {
        assertEquals(listOf("http://127.0.0.1:10/a.png", "http://127.0.0.1:11/a.png"),
            posterUrlCandidates("http://127.0.0.1:10/a.png", "http://127.0.0.1:11"))
        assertEquals(listOf("https://mm.anwap.media/a.jpg"),
            posterUrlCandidates("https://mm.anwap.media/a.jpg", "https://mm.anwap.media"))
    }

    @Test fun `absolute blank relative and protocol relative poster URLs`() {
        assertEquals("", "  ".getWithDomain(""))
        assertEquals("https://cdn.example/a.jpg", "https://cdn.example/a.jpg".getWithDomain(""))
        assertEquals("https://cdn.example/a.jpg", "//cdn.example/a.jpg".getWithDomain("bad base"))
        assertEquals("http://cdn.example/a.jpg", "//cdn.example/a.jpg".getWithDomain("http://example.org/path"))
        assertEquals("https://example.org/a.jpg", "/a.jpg".getWithDomain("https://example.org/path"))
        assertEquals("https://my.anwap.love/a.jpg", "a.jpg".getWithDomain(""))
    }
}
