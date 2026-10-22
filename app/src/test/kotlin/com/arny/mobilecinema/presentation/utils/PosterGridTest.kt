package com.arny.mobilecinema.presentation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PosterGridTest {

    @Test
    fun `phone keeps two columns`() {
        assertEquals(2, PosterGrid.spansFor(360))
        assertEquals(2, PosterGrid.spansFor(411))
    }

    @Test
    fun `tablets get more columns`() {
        assertEquals(3, PosterGrid.spansFor(600))
        assertEquals(4, PosterGrid.spansFor(800))
        assertEquals(5, PosterGrid.spansFor(1024))
        assertEquals(6, PosterGrid.spansFor(1280))
    }

    @Test
    fun `column count is capped for very wide windows`() {
        assertEquals(PosterGrid.MAX_SPANS, PosterGrid.spansFor(2560))
    }

    @Test
    fun `unknown width falls back to phone layout`() {
        assertEquals(PosterGrid.MIN_SPANS, PosterGrid.spansFor(0))
        assertEquals(PosterGrid.MIN_SPANS, PosterGrid.spansFor(-100))
    }

    @Test
    fun `poster width follows cell width and density`() {
        // 360 dp, 2 колонки, x3 → 180 dp ≈ 540 px
        assertEquals(540, PosterGrid.posterWidthPx(widthDp = 360, spans = 2, density = 3f))
        // 800 dp, 4 колонки, x1.5 → 200 dp = 300 px
        assertEquals(300, PosterGrid.posterWidthPx(widthDp = 800, spans = 4, density = 1.5f))
    }

    @Test
    fun `poster width is never zero`() {
        assertTrue(PosterGrid.posterWidthPx(widthDp = 0, spans = 4, density = 2f) >= 1)
        assertTrue(PosterGrid.posterWidthPx(widthDp = 100, spans = 0, density = 2f) >= 1)
    }

    @Test
    fun `cells on tablets stay within the spec range`() {
        // 800 dp / 4 колонки = 200 dp на карточку — больше минимальных 180 dp и меньше 220 dp.
        val cell = 800 / PosterGrid.spansFor(800)
        assertTrue(cell in PosterGrid.MIN_CELL_WIDTH_DP..220)
    }
}
