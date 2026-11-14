package com.arny.mobilecinema.data.db.sources

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteProgram
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Поиск по названию ищет все слова запроса по нормализованному ключу
 * (`titleSearch`/`origTitleSearch`), а не всю строку подстрокой.
 *
 * Причина: «жил был пес» не является подстрокой «Жил-был пёс», а «человек паук новый» —
 * подстрокой «Человек-паук: Новый день»; плюс SQLite не приводит регистр кириллицы.
 */
class MoviesSearchSqlTest {

    private fun titleQuery(search: String) = getMoviesSQL(
        search = search,
        order = AppConstants.Order.NONE,
        searchType = AppConstants.SearchType.TITLE,
        movieTypes = listOf(MovieType.CINEMA),
        likesPriority = true,
        limit = 40,
        offset = 0,
    )

    /** Аргументы, реально доходящие до SQLite: `bindTo` — единственный способ их получить. */
    private class ArgRecorder : SupportSQLiteProgram {
        val args = mutableListOf<Any?>()
        override fun bindNull(index: Int) {
            args.add(null)
        }

        override fun bindLong(index: Int, value: Long) {
            args.add(value)
        }

        override fun bindDouble(index: Int, value: Double) {
            args.add(value)
        }

        override fun bindString(index: Int, value: String) {
            args.add(value)
        }

        override fun bindBlob(index: Int, value: ByteArray) {
            args.add(value)
        }

        override fun clearBindings() = Unit
        override fun close() = Unit
    }

    private fun SimpleSQLiteQuery.bindArgs(): List<Any?> =
        ArgRecorder().also { bindTo(it) }.args

    @Test
    fun `raw title columns are no longer used for matching`() {
        val query = titleQuery("жил был пес").sql
        assertFalse(query.contains("m.title LIKE"), query)
        assertFalse(query.contains("m.origTitle LIKE"), query)
    }

    @Test
    fun `blank query does not add a title predicate`() {
        val query = titleQuery("   ").sql
        assertFalse(query.contains("titleSearch"), query)
    }

    private fun query(search: String, type: String) = getMoviesSQL(
        search, AppConstants.Order.NONE, type, emptyList(),
        likesPriority = false, limit = 40, offset = 0,
    )

    @Test
    fun `actors and directors require all folded words without title ranking`() {
        listOf(
            AppConstants.SearchType.ACTORS to "actorsSearch",
            AppConstants.SearchType.DIRECTORS to "directorsSearch",
        ).forEach { (type, column) ->
            val query = query("ФЁДОРОВ Пётр", type)
            val where = query.sql.substringAfter("WHERE").substringBefore("ORDER BY")
            assertEquals(2, where.split("m.$column LIKE").size - 1)
            assertTrue(where.contains(" AND "))
            assertEquals(listOf("федоров", "петр"), query.bindArgs().take(2))
            assertFalse(query.sql.contains("titleSearch"), query.sql)
            assertEquals(query.sql.count { it == '?' }, query.argCount)
        }
    }

    @Test
    fun `history and catalog use the same predicate for every search mode`() {
        listOf(
            AppConstants.SearchType.TITLE,
            AppConstants.SearchType.ACTORS,
            AppConstants.SearchType.DIRECTORS,
            AppConstants.SearchType.GENRES,
        ).forEach { type ->
            val catalog = query("Чёрный юмор", type)
            val history = getHistorySQL("Чёрный юмор", AppConstants.Order.NONE, type, 40, 0)
            assertEquals(
                catalog.sql.substringAfter("WHERE").substringBefore("ORDER BY").trim(),
                history.sql.substringAfter("WHERE").substringBefore("ORDER BY").trim(),
            )
            val whereArgCount = history.argCount - 2
            assertEquals(catalog.bindArgs().take(whereArgCount), history.bindArgs().take(whereArgCount))
            assertEquals(history.sql.count { it == '?' }, history.argCount)
        }
    }

    @Test
    fun `genre aliases are folded deduplicated and ORed`() {
        val query = query("ЧЁРНЫЙ ЮМОР", AppConstants.SearchType.GENRES)
        val terms = query.bindArgs().dropLast(2)
        assertTrue(query.sql.contains("m.genreSearch LIKE"))
        assertTrue(query.sql.contains(" OR "))
        assertTrue("черный юмор" in terms)
        assertTrue("комеди" in terms, "Existing genre aliases must keep working")
        assertEquals(terms.distinct(), terms)
        assertEquals(query.sql.count { it == '?' }, query.argCount)
    }

    @Test
    fun `genre and country filters fold case ё and punctuation too`() {
        val query = getMoviesSQL(
            "", AppConstants.Order.NONE, AppConstants.SearchType.TITLE, emptyList(),
            genres = listOf("ФИЛЬМ-НУАР", "Драма"), countries = listOf("КОТ-Д’ИВУАР"),
            likesPriority = false, limit = 40, offset = 0,
        )
        val args = query.bindArgs()
        assertTrue(query.sql.contains("m.genreSearch LIKE"))
        assertTrue(query.sql.contains("m.countriesSearch LIKE"))
        assertTrue("кот д ивуар" in args)
        assertTrue("нуар" in args)
        assertTrue("драма" in args)
        assertEquals(query.sql.count { it == '?' }, query.argCount)
    }

    @Test
    fun `separator only input never generates empty SQL parentheses`() {
        listOf(AppConstants.SearchType.TITLE, AppConstants.SearchType.ACTORS,
            AppConstants.SearchType.DIRECTORS, AppConstants.SearchType.GENRES).forEach { type ->
            val query = query("---%_", type)
            assertTrue(query.sql.contains("1 = 1"), query.sql)
            assertEquals(query.sql.count { it == '?' }, query.argCount)
        }
    }
}
