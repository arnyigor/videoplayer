package com.arny.mobilecinema.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRelevanceTest {

    @Test
    fun `exact title match is stronger than prefix and contains`() {
        assertEquals(SearchRelevance.EXACT, SearchRelevance.score("matrix", "Matrix"))
        assertEquals(SearchRelevance.STARTS_WITH, SearchRelevance.score("matrix", "Matrix Reloaded"))
        assertEquals(SearchRelevance.WORD_STARTS_WITH, SearchRelevance.score("matrix", "The Matrix"))
        assertEquals(SearchRelevance.CONTAINS, SearchRelevance.score("matrix", "XmatrixX"))
        assertEquals(SearchRelevance.NO_MATCH, SearchRelevance.score("matrix", "Менталист"))
    }

    @Test
    fun `original title gives latin query a chance to match russian card`() {
        // Русское название не совпадает с латинским запросом, оригинальное — совпадает по слову.
        assertEquals(
            SearchRelevance.WORD_STARTS_WITH,
            SearchRelevance.score("matrix", "Матрица", "The Matrix")
        )
        assertEquals(
            SearchRelevance.NO_MATCH,
            SearchRelevance.score("matrix", "Матрица", "")
        )
    }

    @Test
    fun `ranking puts exact match above fallback noise and keeps order inside equal scores`() {
        data class Item(val title: String, val orig: String = "")

        val items = listOf(
            Item("Список смертников"),
            Item("Менталист"),
            Item("Матрица", "The Matrix"),
            Item("Матриарх"),
        )

        val ranked = SearchRelevance.rank("matrix", items) { listOf(it.title, it.orig) }

        assertEquals("Матрица", ranked.first().title)
        // Нулевые совпадения не перемешиваются: их относительный порядок сохранён.
        assertEquals(
            listOf("Список смертников", "Менталист", "Матриарх"),
            ranked.drop(1).map { it.title }
        )
    }

    @Test
    fun `blank query leaves items untouched`() {
        val items = listOf("b", "a")
        assertEquals(items, SearchRelevance.rank("   ", items) { listOf(it) })
    }

    @Test
    fun `cyrillic query matches cyrillic title`() {
        assertTrue(
            SearchRelevance.score("матрица", "Матрица") >= SearchRelevance.STARTS_WITH
        )
    }

    @Test
    fun `exact title beats exact original title`() {
        assertEquals(SearchRelevance.EXACT, SearchRelevance.score("matrix", "Matrix", "The Matrix"))
        assertEquals(
            SearchRelevance.EXACT_ORIGINAL,
            SearchRelevance.score("the matrix", "Матрица", "The Matrix")
        )
        assertTrue(SearchRelevance.EXACT > SearchRelevance.EXACT_ORIGINAL)
    }

    @Test
    fun `normalization ignores case, extra spaces and yo`() {
        assertEquals(SearchRelevance.EXACT, SearchRelevance.score("  MATRIX  ", "matrix"))
        assertEquals(SearchRelevance.EXACT, SearchRelevance.score("ёлка", "Елка"))
        assertEquals(
            SearchRelevance.EXACT,
            SearchRelevance.score("the   matrix", "The  Matrix")
        )
    }

    @Test
    fun `typo matches as fuzzy but stays below substring`() {
        assertEquals(SearchRelevance.FUZZY, SearchRelevance.score("matrixx", "Matrix"))
        assertTrue(SearchRelevance.FUZZY < SearchRelevance.CONTAINS)
        // Короткие запросы не «угадываются»: иначе выдача забивается случайными подсказками.
        assertEquals(SearchRelevance.NO_MATCH, SearchRelevance.score("mx", "Matrix"))
    }

    @Test
    fun `rating does not affect relevance`() {
        data class Item(val title: String, val rating: Float)
        val ranked = SearchRelevance.rank("matrix", listOf(
            Item("Матрица", 5.1f),
            Item("Матрица: Перезагрузка", 8.9f),
        )) { listOf(it.title) }
        assertEquals("Матрица", ranked.first().title)
    }
}
