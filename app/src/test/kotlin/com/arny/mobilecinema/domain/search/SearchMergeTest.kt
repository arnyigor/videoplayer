package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Слияние двух источников и тождество результатов (аудит v2, §4.3): одна карточка вместо
 * дубля, римейки не склеиваются, точное совпадение выше мусора.
 */
class SearchMergeTest {

    private fun catalog(title: String, year: Int, id: Long) = SearchHit(
        origin = SearchOrigin.CATALOG,
        title = title,
        year = year,
        kind = MovieType.CINEMA,
        stableId = id,
        matchScore = SearchRelevance.score("matrix", title),
    )

    private fun site(title: String, year: Int, ref: String, original: String = "") = SearchHit(
        origin = SearchOrigin.SITE,
        title = title,
        originalTitle = original,
        year = year,
        kind = MovieType.CINEMA,
        siteRef = ref,
        matchScore = SearchRelevance.score("matrix", title, original),
    )

    @Test
    fun `same title and year merges into single catalog card`() {
        val merged = SearchMerge.merge(
            local = listOf(catalog("Матрица", 1999, 1)),
            remote = listOf(site("Матрица", 1999, "films/1091")),
        )

        assertEquals(1, merged.size)
        assertTrue(merged[0].inCatalog)
        assertEquals(1L, merged[0].stableId)
    }

    @Test
    fun `same title but different year is not merged`() {
        val merged = SearchMerge.merge(
            local = listOf(catalog("Матрица", 1999, 1)),
            remote = listOf(site("Матрица", 2021, "films/9999")),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun `item without year is never merged by title only`() {
        val merged = SearchMerge.merge(
            local = listOf(catalog("Матрица", 1999, 1)),
            remote = listOf(site("Матрица", 0, "films/1091")),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun `same page url keeps catalog card`() {
        val local = catalog("Матрица", 1999, 1).copy(siteRef = "films/1091")
        val merged = SearchMerge.merge(
            local = listOf(local),
            remote = listOf(site("Матрица", 1999, "films/1091")),
        )

        assertEquals(1, merged.size)
        assertTrue(merged[0].inCatalog)
    }

    @Test
    fun `relevance wins over origin and noise stays below exact match`() {
        val merged = SearchMerge.merge(
            local = emptyList(),
            remote = listOf(
                site("Список смертников", 2022, "serials/2887"),
                // Латиницу с кириллицей связывает только оригинальное название,
                // поэтому релевантность берём по нему — как в реальной выдаче сайта.
                site("Матрица", 1999, "films/1091", original = "The Matrix"),
                site("Менталист", 2008, "serials/1200"),
            ),
        )

        assertEquals("Матрица", merged.first().title)
        assertEquals(
            listOf("Список смертников", "Менталист"),
            merged.drop(1).map { it.title }
        )
    }

    @Test
    fun `title normalization ignores case punctuation and first letter case`() {
        assertEquals(
            SearchMerge.normalizeTitle("Матрица: Воскрешение!"),
            SearchMerge.normalizeTitle("матрица воскрешение")
        )
        assertEquals("the matrix", SearchMerge.normalizeTitle("  The   Matrix "))
    }
}

class SearchFiltersTest {

    @Test
    fun `encode and decode keep all criteria`() {
        val filters = SearchFilters(
            kinds = listOf(MovieType.SERIAL),
            genre = "Фантастика",
            country = "США",
            yearFrom = 1999,
            yearTo = 2003,
            sort = SearchSort.RATING,
        )

        assertEquals(filters, SearchFilters.decode(filters.encode()))
    }

    @Test
    fun `decode of broken value falls back to empty filters`() {
        assertEquals(SearchFilters(), SearchFilters.decode("мусор"))
        assertEquals(SearchFilters(), SearchFilters.decode(null))
    }

    @Test
    fun `reversed year range is normalized`() {
        val range = SearchFilters(yearFrom = 2020, yearTo = 1990).yearRange
        assertEquals(1990, range?.from)
        assertEquals(2020, range?.to)
    }

    @Test
    fun `only lower bound means open upper bound`() {
        val range = SearchFilters(yearFrom = 2010).yearRange
        assertEquals(2010, range?.from)
        assertEquals(SearchFilters.MAX_YEAR, range?.to)
        assertNull(SearchFilters().yearRange)
    }

    @Test
    fun `active count ignores default sort and reset keeps sort`() {
        val filters = SearchFilters(
            kinds = listOf(MovieType.CINEMA),
            genre = "Драмы",
            yearFrom = 2000,
            sort = SearchSort.ALPHABET,
        )

        assertEquals(3, filters.activeCount)
        assertTrue(filters.isActive)
        assertFalse(SearchFilters().isActive)
        assertEquals(SearchSort.ALPHABET, filters.reset().sort)
        assertFalse(filters.reset().isActive)
    }

    @Test
    fun `empty kinds means both types`() {
        assertEquals(
            listOf(MovieType.CINEMA, MovieType.SERIAL),
            SearchFilters().movieTypes
        )
    }
}
