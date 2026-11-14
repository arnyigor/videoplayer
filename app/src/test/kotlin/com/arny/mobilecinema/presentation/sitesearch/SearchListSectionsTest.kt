package com.arny.mobilecinema.presentation.sitesearch

import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchOrigin
import com.arny.mobilecinema.domain.search.SearchRelevance
import com.arny.mobilecinema.domain.search.SearchSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** S-013: секции «каталог отфильтрован» / «сайт без фильтров» и доступность фильтров. */
class SearchListSectionsTest {

    private fun hit(
        title: String,
        origin: SearchOrigin,
        matchScore: Int = SearchRelevance.EXACT,
    ) = SearchHit(
        origin = origin,
        title = title,
        kind = MovieType.CINEMA,
        matchScore = matchScore,
        stableId = if (origin == SearchOrigin.CATALOG) 1L else null,
        siteRef = if (origin == SearchOrigin.CATALOG) "" else "/films/1",
    )

    @Test
    fun `mixed result is split into two titled sections`() {
        val items = SearchListSections.build(
            hits = listOf(
                hit("Каталог 1", SearchOrigin.CATALOG),
                hit("Сайт 1", SearchOrigin.SITE),
            ),
            source = SearchSource.ALL,
            filters = SearchFilters(genres = listOf("Драма")),
        )
        assertEquals(4, items.size)
        assertEquals(
            SearchListItem.Header(SearchListItem.Header.Type.CATALOG_FILTERED),
            items[0]
        )
        assertTrue(items[1] is SearchListItem.Hit)
        assertEquals(
            SearchListItem.Header(SearchListItem.Header.Type.SITE_UNFILTERED),
            items[2]
        )
        assertTrue(items[3] is SearchListItem.Hit)
    }

    @Test
    fun `single source has no headers`() {
        val catalogOnly = SearchListSections.build(
            hits = listOf(hit("Каталог 1", SearchOrigin.CATALOG)),
            source = SearchSource.ALL,
            filters = SearchFilters(),
        )
        assertEquals(1, catalogOnly.size)
        assertTrue(catalogOnly.single() is SearchListItem.Hit)

        val siteOnly = SearchListSections.build(
            hits = listOf(hit("Сайт 1", SearchOrigin.SITE)),
            source = SearchSource.REMOTE,
            filters = SearchFilters(),
        )
        assertEquals(1, siteOnly.size)
        assertTrue(siteOnly.single() is SearchListItem.Hit)
    }

    @Test
    fun `empty result has no items at all`() {
        assertTrue(
            SearchListSections.build(emptyList(), SearchSource.ALL, SearchFilters()).isEmpty()
        )
    }

    /**
     * Поиск сайта сортирует по рейтингу, а не по совпадению: без секции «Похожие» чужие
     * тайтлы стоят в одном списке с настоящими совпадениями.
     */
    @Test
    fun `site hits without any name match are moved to a separate trailing section`() {
        val items = SearchListSections.build(
            hits = listOf(
                hit("Найдено", SearchOrigin.SITE),
                hit("Игра в молчанку", SearchOrigin.SITE, SearchRelevance.NO_MATCH),
            ),
            source = SearchSource.REMOTE,
            filters = SearchFilters(),
        )
        assertEquals(3, items.size)
        assertTrue(items[0] is SearchListItem.Hit)
        assertEquals(
            SearchListItem.Header(SearchListItem.Header.Type.SITE_SIMILAR),
            items[1]
        )
        assertEquals("Игра в молчанку", (items[2] as SearchListItem.Hit).hit.title)
    }

    @Test
    fun `site hits with matches keep single source unlabelled`() {
        val items = SearchListSections.build(
            hits = listOf(
                hit("Сайт 1", SearchOrigin.SITE),
                hit("Сайт 2", SearchOrigin.SITE, SearchRelevance.CONTAINS),
            ),
            source = SearchSource.REMOTE,
            filters = SearchFilters(),
        )
        assertEquals(2, items.size)
        assertTrue(items.all { it is SearchListItem.Hit })
    }

    @Test
    fun `actor director and genre searches are not judged by film titles`() {
        listOf(
            com.arny.mobilecinema.data.repository.AppConstants.SearchType.ACTORS,
            com.arny.mobilecinema.data.repository.AppConstants.SearchType.DIRECTORS,
            com.arny.mobilecinema.data.repository.AppConstants.SearchType.GENRES,
        ).forEach { type ->
            val items = SearchListSections.build(
                listOf(hit("Film title", SearchOrigin.SITE, SearchRelevance.NO_MATCH)),
                SearchSource.REMOTE, SearchFilters(), type,
            )
            assertEquals(1, items.size)
            assertTrue(items.single() is SearchListItem.Hit)
        }
    }

    @Test
    fun `filters are unavailable only for site-only mode`() {
        assertTrue(SearchListSections.areFiltersAvailable(SearchSource.ALL))
        assertTrue(SearchListSections.areFiltersAvailable(SearchSource.LOCAL))
        assertFalse(SearchListSections.areFiltersAvailable(SearchSource.REMOTE))
    }
}
