package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.domain.models.ViewMovie

class HistoryPagingSource(
    private val historyDao: HistoryDao,
    private val search: String,
    private val order: String,
    private val searchType: String,
) : PagingSource<Int, ViewMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        val sqlResult = historyDao.getMovies(
            getHistorySQL(
                search = search,
                order = order,
                searchType = searchType,
                limit = params.loadSize,
                offset = page * params.loadSize
            )
        )
        return try {
            LoadResult.Page(
                data = sqlResult,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (sqlResult.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ViewMovie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}