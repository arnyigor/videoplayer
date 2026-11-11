package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регресс на мульти-выбор жанров: список должен переживать encode/decode
 * (шторка фильтров → бандл → поиск), а старый одиночный формат — читаться.
 */
class SearchFiltersGenresTest {

    @Test
    fun `multiple genres survive encode decode roundtrip`() {
        val filters = SearchFilters(genres = listOf("Аниме", "Мультфильм", "Фантастика"))
        val restored = SearchFilters.decode(filters.encode())

        assertEquals(filters.genres, restored.genres)
    }

    @Test
    fun `legacy single genre payload still decodes as one item`() {
        // Формат из версии с одним жанром: parts[1] = "Комедия".
        val legacy = listOf("", "Комедия", "", "", "", SearchSort.RELEVANCE.name)
            .joinToString("\u001f")

        assertEquals(listOf("Комедия"), SearchFilters.decode(legacy).genres)
    }

    @Test
    fun `empty genre list encodes to empty and decodes back`() {
        val restored = SearchFilters.decode(SearchFilters().encode())

        assertTrue(restored.genres.isEmpty())
        assertFalse(restored.isActive)
    }

    @Test
    fun `genres count as one active criterion`() {
        val one = SearchFilters(genres = listOf("Драма"))
        val two = SearchFilters(genres = listOf("Драма", "Комедия"))

        assertEquals(1, one.activeCount)
        assertEquals(1, two.activeCount)
    }

    @Test
    fun `kinds and genres are counted separately`() {
        val filters = SearchFilters(kinds = listOf(MovieType.CINEMA), genres = listOf("Драма"))

        assertEquals(2, filters.activeCount)
    }

    @Test
    fun `reset keeps sort but clears genres`() {
        val filters = SearchFilters(genres = listOf("Драма"), sort = SearchSort.RATING)
        val reset = filters.reset()

        assertTrue(reset.genres.isEmpty())
        assertEquals(SearchSort.RATING, reset.sort)
    }
}
