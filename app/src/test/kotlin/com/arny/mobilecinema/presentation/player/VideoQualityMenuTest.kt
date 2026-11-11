package com.arny.mobilecinema.presentation.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoQualityMenuTest {
    @Test
    fun `resolutions sort by height rather than label or width`() {
        val menu = videoQualityMenu(listOf(
            Triple(848, 480, "sd"), Triple(2560, 720, "wide"), Triple(1920, 1080, "fullhd")))
        assertEquals(listOf("fullhd", "wide", "sd"), menu.map { it.second })
        assertEquals(listOf("1080p (1920 × 1080)", "720p (2560 × 720)", "480p (848 × 480)"), menu.map { it.first })
    }

    @Test
    fun `equal heights sort by width`() {
        val menu = videoQualityMenu(listOf(Triple(960, 720, 1), Triple(1280, 720, 2)))
        assertEquals(listOf(2, 1), menu.map { it.second })
    }

    @Test
    fun `equal resolutions keep distinct track references and labels`() {
        val menu = videoQualityMenu(listOf(Triple(1920, 1080, "avc"), Triple(1920, 1080, "hevc")))
        assertEquals(listOf("avc", "hevc"), menu.map { it.second })
        assertEquals(listOf("1080p (1920 × 1080) (1)", "1080p (1920 × 1080) (2)"), menu.map { it.first })
    }

    @Test
    fun `one actual resolution remains visible`() {
        assertEquals(listOf("480p (854 × 480)" to "only"), videoQualityMenu(listOf(Triple(854, 480, "only"))))
    }

    @Test
    fun `unknown dimensions do not create fictitious qualities`() {
        assertTrue(videoQualityMenu(listOf(Triple(-1, -1, 1), Triple(0, 720, 2), Triple(1280, 0, 3))).isEmpty())
        assertTrue(videoQualityMenu(emptyList<Triple<Int, Int, String>>()).isEmpty())
    }
}
