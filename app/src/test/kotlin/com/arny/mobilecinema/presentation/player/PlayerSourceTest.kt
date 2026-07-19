package com.arny.mobilecinema.presentation.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSourceTest {

    @Test
    fun `sanitizeDashManifest removes secondary interkh base url when primary cdnr exists`() {
        val manifest = """
            <MPD>
              <BaseURL>https://cdnr.interkh.com/05_26/11/10/N6AQITB3/</BaseURL>
              <BaseURL>https://x-bc.interkh.com/05_26/11/10/N6AQITB3/</BaseURL>
              <Period></Period>
            </MPD>
        """.trimIndent()

        val result = sanitizeDashManifest(manifest)

        assertTrue(result.contains("https://cdnr.interkh.com/05_26/11/10/N6AQITB3/"))
        assertFalse(result.contains("https://x-bc.interkh.com/05_26/11/10/N6AQITB3/"))
    }

    @Test
    fun `sanitizeDashManifest keeps manifest unchanged without primary cdnr`() {
        val manifest = """
            <MPD>
              <BaseURL>https://x-bc.interkh.com/05_26/11/10/N6AQITB3/</BaseURL>
            </MPD>
        """.trimIndent()

        assertTrue(sanitizeDashManifest(manifest).contains("https://x-bc.interkh.com/05_26/11/10/N6AQITB3/"))
    }
}
