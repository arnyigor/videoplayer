package com.arny.mobilecinema.presentation.catalog

import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogPagingTest {

    private fun movie(id: Long) = ViewMovie(
        dbId = id,
        title = "Movie $id",
        type = MovieType.CINEMA.value,
        year = 2026,
    )

    private fun page(from: Long, size: Int): List<ViewMovie> =
        (from until from + size).map { movie(it) }

    @Test
    fun `section key maps to catalog section with films as fallback`() {
        assertEquals(CatalogSection.SERIES, CatalogSection.fromKey("SERIES"))
        assertEquals(CatalogSection.FILMS, CatalogSection.fromKey("FILMS"))
        assertEquals(CatalogSection.FILMS, CatalogSection.fromKey(null))
        assertEquals(CatalogSection.FILMS, CatalogSection.fromKey("unknown"))
    }

    @Test
    fun `sections use different movie types`() {
        assertEquals(MovieType.CINEMA, CatalogSection.FILMS.movieType)
        assertEquals(MovieType.SERIAL, CatalogSection.SERIES.movieType)
    }

    @Test
    fun `sort options produce different catalog orders`() {
        val orders = CatalogSort.entries.map { it.order }
        assertEquals(orders.size, orders.distinct().size)
    }

    @Test
    fun `next page appends items and resets loading flags`() {
        val state = CatalogPaging.withNextPage(
            CatalogUiState(isLoading = true),
            page(0, CatalogPaging.PAGE_SIZE),
        )

        assertEquals(CatalogPaging.PAGE_SIZE, state.items.size)
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMore)
        assertFalse(state.endReached)
        assertFalse(state.error)
    }

    @Test
    fun `short page ends the catalog`() {
        val state = CatalogPaging.withNextPage(CatalogUiState(), page(0, 5))

        assertTrue(state.endReached)
        assertFalse(state.isLoading)
    }

    @Test
    fun `empty page ends the catalog`() {
        val state = CatalogPaging.withNextPage(
            CatalogUiState(items = page(0, 10), isLoadingMore = true),
            emptyList(),
        )

        assertEquals(10, state.items.size)
        assertTrue(state.endReached)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `second page is appended after the first one`() {
        val first = CatalogPaging.withNextPage(CatalogUiState(), page(0, 24))
        val second = CatalogPaging.withNextPage(
            CatalogPaging.withLoadingMore(first),
            page(24, 24),
        )

        assertEquals((0L until 48L).toList(), second.items.map { it.dbId })
        assertFalse(second.endReached)
    }

    @Test
    fun `duplicate ids are not appended twice`() {
        val first = CatalogPaging.withNextPage(CatalogUiState(), page(0, 24))
        val second = CatalogPaging.withNextPage(first, page(0, 24))

        assertEquals(24, second.items.size)
    }

    @Test
    fun `changing filter clears items and restarts loading`() {
        val loaded = CatalogPaging.withNextPage(CatalogUiState(), page(0, 5))
        val filtered = CatalogPaging.withFilter(loaded, genre = "драма", sort = CatalogSort.RATING)

        assertEquals(emptyList<ViewMovie>(), filtered.items)
        assertEquals("драма", filtered.genre)
        assertEquals(CatalogSort.RATING, filtered.sort)
        assertTrue(filtered.isLoading)
        assertFalse(filtered.endReached)
        assertFalse(filtered.error)
    }

    @Test
    fun `changing filter keeps already loaded genres`() {
        val withGenres = CatalogPaging.withGenres(CatalogUiState(), listOf("боевик", "драма"))
        val filtered = CatalogPaging.withFilter(withGenres, genre = null)

        assertEquals(listOf("боевик", "драма"), filtered.genres)
        assertNull(filtered.genre)
    }

    @Test
    fun `error keeps loaded items and stops spinners`() {
        val loaded = CatalogPaging.withNextPage(CatalogUiState(), page(0, 3))
        val failed = CatalogPaging.withError(CatalogPaging.withLoadingMore(loaded))

        assertEquals(3, failed.items.size)
        assertTrue(failed.error)
        assertFalse(failed.isLoading)
        assertFalse(failed.isLoadingMore)
    }

    @Test
    fun `loading more does not clear the catalog`() {
        val loaded = CatalogPaging.withNextPage(CatalogUiState(), page(0, 24))
        val loading = CatalogPaging.withLoadingMore(loaded)

        assertEquals(24, loading.items.size)
        assertTrue(loading.isLoadingMore)
        assertFalse(loading.isLoading)
    }
}
