package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PosterUrlTest {
    @Test fun `absolute blank relative and protocol relative poster URLs`() {
        assertEquals("", "  ".getWithDomain(""))
        assertEquals("https://cdn.example/a.jpg", "https://cdn.example/a.jpg".getWithDomain(""))
        assertEquals("https://cdn.example/a.jpg", "//cdn.example/a.jpg".getWithDomain("bad base"))
        assertEquals("http://cdn.example/a.jpg", "//cdn.example/a.jpg".getWithDomain("http://example.org/path"))
        assertEquals("https://example.org/a.jpg", "/a.jpg".getWithDomain("https://example.org/path"))
        assertEquals("https://my.anwap.love/a.jpg", "a.jpg".getWithDomain(""))
    }
}
