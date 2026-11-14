package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SiteSearchItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Размеры страниц выдачи сайта разные (фильмы 10, сериалы 20 — проверено на живом сайте),
 * поэтому стоп-условие пагинации считается по каждой секции отдельно.
 */
class SearchPagingTest {

    private fun items(type: MovieType, count: Int) =
        (1..count).map { SiteSearchItem(pageUrl = "/x/$it", title = "t$it", type = type) }

    @Test
    fun `full serials page keeps paging even when films page is empty`() {
        assertTrue(SearchPaging.hasMore(items(MovieType.SERIAL, SearchPaging.SERIALS_PAGE_SIZE)))
    }

    @Test
    fun `short serials page with full films page keeps paging`() {
        val page = items(MovieType.SERIAL, 3) + items(MovieType.CINEMA, SearchPaging.FILMS_PAGE_SIZE)
        assertTrue(SearchPaging.hasMore(page))
    }

    @Test
    fun `both sections short means the last page`() {
        val page = items(MovieType.SERIAL, 12) + items(MovieType.CINEMA, 4)
        assertFalse(SearchPaging.hasMore(page))
    }

    @Test
    fun `empty page has nothing more`() {
        assertFalse(SearchPaging.hasMore(emptyList()))
    }
}
