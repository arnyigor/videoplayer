package com.arny.mobilecinema.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ключ поиска по базе. SQLite не приводит регистр кириллицы (`lower()`/`LIKE` умеют только
 * ASCII) и не знает про `ё`, поэтому ключ считается в Kotlin и хранится в колонке.
 */
class SearchKeyTest {

    @Test
    fun `dash and ё are normalised for a title with both`() {
        assertEquals("жил был пес", SearchRelevance.searchKey("Жил-был пёс"))
    }

    @Test
    fun `colon and dash do not break word matching`() {
        assertEquals(
            "человек паук новый день",
            SearchRelevance.searchKey("Человек-паук: Новый день")
        )
    }

    @Test
    fun `underscore, brackets and quotes become spaces`() {
        assertEquals(
            "матрица перезагрузка 2",
            SearchRelevance.searchKey("Матрица_Перезагрузка (2)")
        )
    }

    @Test
    fun `case is folded and repeated separators collapse`() {
        assertEquals("the matrix", SearchRelevance.searchKey("  THE   Matrix  "))
    }

    @Test
    fun `query tokens are normalised the same way as stored keys`() {
        assertEquals(listOf("жил", "был", "пес"), SearchRelevance.tokens("Жил был Пёс"))
        assertEquals(listOf("человек", "паук", "новый"), SearchRelevance.tokens("человек-паук новый"))
    }

    @Test
    fun `blank query has no tokens`() {
        assertEquals(emptyList<String>(), SearchRelevance.tokens("   "))
    }
}
