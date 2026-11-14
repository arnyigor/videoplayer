package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.arny.mobilecinema.data.db.daos.FavoritesDao
import com.arny.mobilecinema.data.db.models.FavoriteEntity
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Tests the actual production source and its bound LIMIT/OFFSET, not a replacement Pager. */
class FavoritesPagingSourceTest {
    private fun source(dao: FavoritesDao = RowsDao()) = FavoritesPagingSource(
        dao, "", AppConstants.Order.TITLE, AppConstants.SearchType.TITLE,
    )

    @Test fun `default paging initial 60 then append 20 visits all 95 records exactly once`() = runTest {
        val source = source()
        val first = source.load(PagingSource.LoadParams.Refresh(null, 60, false)) as PagingSource.LoadResult.Page
        assertEquals((1L..60L).toList(), first.data.map { it.dbId })
        assertEquals(60, first.nextKey)
        val second = source.load(PagingSource.LoadParams.Append(first.nextKey!!, 20, false)) as PagingSource.LoadResult.Page
        val third = source.load(PagingSource.LoadParams.Append(second.nextKey!!, 20, false)) as PagingSource.LoadResult.Page
        val ids = (first.data + second.data + third.data).map { it.dbId }
        assertEquals((1L..95L).toList(), ids)
        assertEquals(ids.size, ids.distinct().size)
        assertNull(third.nextKey)
    }

    @Test fun `changing append sizes neither skips nor repeats rows`() = runTest {
        val source = source()
        val ids = mutableListOf<Long>()
        var key: Int? = null
        listOf(20, 31, 17, 27).forEachIndexed { index, size ->
            val params = if (index == 0) PagingSource.LoadParams.Refresh(key, size, false)
                else PagingSource.LoadParams.Append(key!!, size, false)
            val page = source.load(params) as PagingSource.LoadResult.Page
            ids += page.data.map { it.dbId }
            key = page.nextKey
        }
        assertEquals((1L..95L).toList(), ids)
        // A full final page needs one empty append to discover the end without a count query.
        assertEquals(95, key)
        val end = source.load(PagingSource.LoadParams.Append(key!!, 20, false)) as PagingSource.LoadResult.Page
        assertTrue(end.data.isEmpty())
        assertNull(end.nextKey)
    }

    @Test fun `refresh away from zero supports prepend and append without a gap`() = runTest {
        val source = source()
        val middle = source.load(PagingSource.LoadParams.Refresh(40, 30, false)) as PagingSource.LoadResult.Page
        val before = source.load(PagingSource.LoadParams.Prepend(middle.prevKey!!, 20, false)) as PagingSource.LoadResult.Page
        val after = source.load(PagingSource.LoadParams.Append(middle.nextKey!!, 20, false)) as PagingSource.LoadResult.Page
        assertEquals((21L..90L).toList(), (before.data + middle.data + after.data).map { it.dbId })
        val state = PagingState(listOf(middle), 15, PagingConfig(pageSize = 20), 0)
        assertEquals(40, source.getRefreshKey(state))
    }

    @Test fun `empty data ends both directions and has no refresh anchor`() = runTest {
        val source = source(RowsDao(0))
        val page = source.load(PagingSource.LoadParams.Refresh(null, 60, false)) as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
        assertNull(source.getRefreshKey(PagingState(emptyList(), null, PagingConfig(20), 0)))
    }

    @Test fun `metadata failure is exposed as a retryable load error`() = runTest {
        val source = source(object : RowsDao() {
            override suspend fun updateLatestTime(time: Long) { error("database unavailable") }
        })
        assertTrue(source.load(PagingSource.LoadParams.Refresh(null, 20, false)) is PagingSource.LoadResult.Error)
    }

    @Test fun `favorites share normalized search predicates with catalog and history`() = runTest {
        listOf(AppConstants.SearchType.TITLE, AppConstants.SearchType.ACTORS,
            AppConstants.SearchType.DIRECTORS, AppConstants.SearchType.GENRES).forEach { type ->
            var captured: SupportSQLiteQuery? = null
            val dao = object : RowsDao(0) {
                override suspend fun getFavoriteMovies(query: SupportSQLiteQuery): List<ViewMovie> {
                    captured = query
                    return emptyList()
                }
            }
            val source = FavoritesPagingSource(dao, "ПЁТР Фёдоров", AppConstants.Order.NONE, type)
            assertTrue(source.load(PagingSource.LoadParams.Refresh(null, 20, false)) is PagingSource.LoadResult.Page)
            val query = checkNotNull(captured)
            val expected = getHistorySQL("ПЁТР Фёдоров", AppConstants.Order.NONE, type, 20, 0)
            assertEquals(
                expected.sql.substringAfter("WHERE").substringBefore("ORDER BY").substringBefore("LIMIT").trim(),
                query.sql.substringAfter("WHERE").substringBefore("LIMIT").trim(),
            )
            assertEquals(query.sql.count { it == '?' }, query.argCount)
        }
    }

    private open class RowsDao(count: Int = 95) : FavoritesDao {
        private val rows = (1L..count.toLong()).map { ViewMovie(dbId = it, title = "Favorite $it") }
        override suspend fun getFavoriteMovies(query: SupportSQLiteQuery): List<ViewMovie> {
            val args = mutableMapOf<Int, Any?>()
            query.bindTo(object : SupportSQLiteProgram {
                override fun bindNull(index: Int) { args[index] = null }
                override fun bindLong(index: Int, value: Long) { args[index] = value }
                override fun bindDouble(index: Int, value: Double) { args[index] = value }
                override fun bindString(index: Int, value: String) { args[index] = value }
                override fun bindBlob(index: Int, value: ByteArray) { args[index] = value }
                override fun clearBindings() { args.clear() }
                override fun close() = Unit
            })
            val limit = (args[query.argCount - 1] as Number).toInt()
            val offset = (args[query.argCount] as Number).toInt()
            return rows.drop(offset).take(limit)
        }
        override suspend fun updateLatestTime(time: Long) = Unit
        override suspend fun getMovies(query: SupportSQLiteQuery) = getFavoriteMovies(query)
        override fun insert(obj: FavoriteEntity): Long = error("Not used")
        override fun update(obj: FavoriteEntity): Int = error("Not used")
        override fun insertAll(objs: List<FavoriteEntity>) = error("Not used")
        override fun getCountForMovie(movieDbId: Long): Int = error("Not used")
        override fun deleteFavorite(movieDbId: Long?): Int = error("Not used")
        override fun deleteAllFavorites(): Int = error("Not used")
        override fun getFavoritesCount(): Int = rows.size
    }
}
