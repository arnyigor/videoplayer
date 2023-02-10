package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.delay

class MainPagingSource(
    private val dao: MovieDao,
    private val search: String,
) : PagingSource<Int, ViewMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0

        return try {
            val list = if (search.isNotBlank()) {
                dao.getPagedList(search, params.loadSize, page * params.loadSize)
            } else {
                dao.getPagedList(params.loadSize, page * params.loadSize)
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