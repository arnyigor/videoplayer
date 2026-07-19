package com.arny.mobilecinema.presentation.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateServiceUrlTest {

    @Test
    fun `normalizeUpdateUrl uses entry point for relative film url`() {
        val url = normalizeUpdateUrl(
            url = "/films/37033",
            entryPointBaseUrl = "https://my.anwap.love/",
            savedBaseUrl = "https://anwap.media"
        )

        assertEquals("https://my.anwap.love/films/37033", url)
    }

    @Test
    fun `normalizeUpdateUrl keeps absolute url unchanged`() {
        val url = normalizeUpdateUrl(
            url = " https://my.anwap.love/serials/1 ",
            entryPointBaseUrl = "https://my.anwap.love",
            savedBaseUrl = ""
        )

        assertEquals("https://my.anwap.love/serials/1", url)
    }

    @Test
    fun `normalizeUpdateUrl fixes protocol-relative url`() {
        val url = normalizeUpdateUrl(
            url = "//my.anwap.love/films/1",
            entryPointBaseUrl = "https://my.anwap.love",
            savedBaseUrl = ""
        )

        assertEquals("https://my.anwap.love/films/1", url)
    }

    @Test
    fun `getUpdateBaseUrl falls back to saved url only when entry point is invalid`() {
        val url = getUpdateBaseUrl(
            entryPointBaseUrl = "",
            savedBaseUrl = "https://fallback.example/"
        )

        assertEquals("https://fallback.example", url)
    }
}
