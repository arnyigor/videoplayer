package com.arny.mobilecinema.data.db.sources

import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleIntRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Поиск по локальному каталогу: ищем по названию и оригинальному названию,
 * а выдачу упорядочиваем по совпадению с запросом (аудит v2, §4.2–4.4).
 */
class SearchSqlTest {

    private fun moviesSql(search: String, order: String = AppConstants.Order.SMART) =
        getMoviesSQL(
            search = search,
            order = order,
            searchType = AppConstants.SearchType.TITLE,
            movieTypes = listOf(MovieType.CINEMA, MovieType.SERIAL),
            likesPriority = true,
            limit = 20,
            offset = 0,
        )

    @Test
    fun `title search also looks into original title`() {
        val query = moviesSql("matrix")

        assertTrue(query.sql.contains("m.title LIKE"))
        assertTrue(query.sql.contains("m.origTitle LIKE"))
    }

    @Test
    fun `search results are ordered by relevance before popularity`() {
        val query = moviesSql("matrix")

        val orderBy = query.sql.substringAfter("ORDER BY")
        assertTrue(orderBy.trimStart().startsWith("CASE"))
        assertTrue(orderBy.contains("WHEN m.title LIKE ? THEN 0"))
        assertTrue(orderBy.contains("WHEN m.title LIKE ? || '%' THEN 1"))
        // Популярность остаётся вторичным ключом сортировки.
        assertTrue(orderBy.contains("m.year DESC, m.likes DESC"))
    }

    @Test
    fun `two word query searches raw and underscore variants`() {
        val query = moviesSql("matrix 2")

        // Раньше сырой запрос в SQL не попадал вовсе, только варианты с подчёркиванием.
        // Каждый вариант — два поля (title, origTitle).
        val where = query.sql.substringAfter("WHERE").substringBefore("ORDER BY")
        assertEquals(4, Regex("m\\.title LIKE").findAll(where).count())
        assertEquals(4, Regex("m\\.origTitle LIKE").findAll(where).count())
    }

    @Test
    fun `no search means no relevance ordering`() {
        val query = moviesSql("")

        assertFalse(query.sql.substringAfter("ORDER BY").trimStart().startsWith("CASE"))
        assertFalse(query.sql.contains("m.origTitle LIKE"))
    }

    @Test
    fun `relevance and filters keep placeholder count in sync with bind args`() {
        val cases = listOf(
            moviesSql("matrix"),
            moviesSql("matrix 2"),
            moviesSql("ма"),
            getMoviesSQL(
                search = "matrix",
                order = AppConstants.Order.YEAR_DESC,
                searchType = AppConstants.SearchType.TITLE,
                movieTypes = listOf(MovieType.CINEMA),
                genres = listOf("Фантастика"),
                countries = listOf("США"),
                years = SimpleIntRange(1990, 2020),
                likesPriority = false,
                limit = 10,
                offset = 40,
            ),
        )

        cases.forEach { query ->
            val placeholders = query.sql.count { it == '?' }
            assertEquals(
                placeholders,
                query.argCount,
                "число ? не совпало с числом аргументов в: ${query.sql}"
            )
        }
    }
}
