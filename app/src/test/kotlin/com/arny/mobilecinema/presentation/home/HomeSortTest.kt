package com.arny.mobilecinema.presentation.home

import com.arny.mobilecinema.data.db.sources.getMoviesSQL
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.presentation.catalog.CatalogSort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomeSortTest {
    @Test fun `fixed recommendation rails never cover an explicitly sorted grid`() {
        assertTrue(isRecommendedHomeOrder(""))
        assertTrue(isRecommendedHomeOrder(AppConstants.Order.SMART))
        listOf(AppConstants.Order.NONE, AppConstants.Order.TITLE, AppConstants.Order.RATINGS,
            AppConstants.Order.YEAR_ASC, AppConstants.Order.YEAR_DESC).forEach {
            assertFalse(isRecommendedHomeOrder(it), "Explicit order $it must show the actual ordered results")
        }
    }

    @Test fun `catalog recommended does not alias updated order`() {
        assertEquals(AppConstants.Order.SMART, CatalogSort.RECOMMENDED.order)
        assertEquals(6, CatalogSort.entries.map { it.order }.distinct().size)
        fun sql(sort: CatalogSort) = getMoviesSQL(search = "", order = sort.order, searchType = "",
            movieTypes = emptyList(), likesPriority = true, limit = 20, offset = 0).sql
        assertNotEquals(sql(CatalogSort.RECOMMENDED), sql(CatalogSort.UPDATED))
    }
}
