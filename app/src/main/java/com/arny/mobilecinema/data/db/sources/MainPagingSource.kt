package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.ViewMovie

class MainPagingSource(
    private val dao: MovieDao,
    private val search: String,
    private val order: String,
    private val searchType: String,
) : PagingSource<Int, ViewMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        return try {
            val list = when {
                search.isNotBlank() && order.isNotBlank() -> {
                    dao.getPagedListBySearch(
                        search = search,
                        searchType = searchType,
                        order = order,
                        limit = params.loadSize,
                        offset = page * params.loadSize
                    )
                }
                search.isNotBlank() -> {
                    when (searchType) {
                        AppConstants.SearchType.TITLE -> dao.getPagedListBySearchTitle(
                            search = search,
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.SearchType.DIRECTORS -> dao.getPagedListBySearchDirectors(
                            search = search,
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.SearchType.ACTORS -> dao.getPagedListBySearchActors(
                            search = search,
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.SearchType.GENRES -> dao.getPagedListBySearchGenres(
                            search = search,
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        else -> dao.getPagedListBySearchTitle(
                            search = search,
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                    }
                }
                order.isNotBlank() -> {
                    when (order) {
                        AppConstants.Order.TITLE -> dao.getPagedListOrderTitle(
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.Order.RATINGS -> dao.getPagedListOrderRatings(
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.Order.YEAR_DESC -> dao.getPagedListOrderYearD(
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        AppConstants.Order.YEAR_ASC -> dao.getPagedListOrderYearA(
                            limit = params.loadSize,
                            offset = page * params.loadSize
                        )
                        else -> dao.getPagedListOrderUpdated(params.loadSize, page * params.loadSize)
                    }
                }
                else -> dao.getPagedListOrderUpdated(params.loadSize, page * params.loadSize)
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