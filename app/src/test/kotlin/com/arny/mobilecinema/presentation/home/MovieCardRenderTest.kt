package com.arny.mobilecinema.presentation.home

import com.arny.mobilecinema.domain.models.ViewMovie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MovieCardRenderTest {

    /**
     * рейтинг форматируется через Locale.getDefault(), поэтому разделитель
     * дробной части зависит от локали тестового JVM — нормализуем его.
     */
    private fun String?.withDotSeparator(): String? = this?.replace(',', '.')

    @Test
    fun `ratingLabel prefers IMDb over KP`() {
        val movie = ViewMovie(ratingImdb = 7.5, ratingKp = 8.0)

        assertEquals("IMDb 7.5", movie.ratingLabel().withDotSeparator())
    }

    @Test
    fun `ratingLabel falls back to KP when IMDb is absent`() {
        val movie = ViewMovie(ratingImdb = 0.0, ratingKp = 8.2)

        assertEquals("KP 8.2", movie.ratingLabel().withDotSeparator())
    }

    @Test
    fun `ratingLabel is null when both ratings are absent`() {
        val movie = ViewMovie(ratingImdb = 0.0, ratingKp = 0.0)

        assertNull(movie.ratingLabel())
    }
}
