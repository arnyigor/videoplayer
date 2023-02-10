package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.domain.models.ViewMovie

class HistoryPagingSource(
    private val dao: HistoryDao,
    private val search: String,
) : PagingSource<Int, ViewMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        val historyIds = dao.getHistoryIds().map { it.movie_dbid }
        return try {
            val list = if (search.isNotBlank()) {
                dao.getPagedList(historyIds, search, params.loadSize, page * params.loadSize)
            } else {
                dao.getPagedList(historyIds, params.loadSize, page * params.loadSize)
            }
            LoadResult.Page(
                data = list,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (list.isEmpty()) null else page + 1
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