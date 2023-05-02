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
                search.isNotBlank() -> {
                    when (searchType) {
                        AppConstants.SearchType.TITLE -> when (order) {
                            AppConstants.Order.TITLE -> dao.getPagedListBySearchTitleOrderTitle(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.RATINGS -> dao.getPagedListBySearchTitleOrderRating(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.NONE -> dao.getPagedListBySearchTitleOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_DESC -> dao.getPagedListBySearchTitleOrderYearD(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_ASC -> dao.getPagedListBySearchTitleOrderYearA(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            else -> dao.getPagedListBySearchTitleOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )
                        }

                        AppConstants.SearchType.DIRECTORS -> when (order) {
                            AppConstants.Order.TITLE -> dao.getPagedListBySearchDirectorsOrderTitle(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.RATINGS -> dao.getPagedListBySearchDirectorsOrderRating(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.NONE -> dao.getPagedListBySearchDirectorsOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_DESC -> dao.getPagedListBySearchDirectorsOrderYearD(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_ASC -> dao.getPagedListBySearchDirectorsOrderYearA(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            else -> dao.getPagedListBySearchDirectorsOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )
                        }

                        AppConstants.SearchType.ACTORS -> when (order) {
                            AppConstants.Order.TITLE -> dao.getPagedListBySearchActorsOrderTitle(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.RATINGS -> dao.getPagedListBySearchActorsOrderRating(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.NONE -> dao.getPagedListBySearchActorsOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_DESC -> dao.getPagedListBySearchActorsOrderYearD(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_ASC -> dao.getPagedListBySearchActorsOrderYearA(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            else -> dao.getPagedListBySearchActorsOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )
                        }

                        AppConstants.SearchType.GENRES -> when (order) {
                            AppConstants.Order.TITLE -> dao.getPagedListBySearchGenresOrderTitle(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.RATINGS -> dao.getPagedListBySearchGenresOrderRating(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.NONE -> dao.getPagedListBySearchGenresOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_DESC -> dao.getPagedListBySearchGenresOrderYearD(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            AppConstants.Order.YEAR_ASC -> dao.getPagedListBySearchGenresOrderYearA(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )

                            else -> dao.getPagedListBySearchGenresOrderUpdated(
                                search = search,
                                limit = params.loadSize,
                                offset = page * params.loadSize
                            )
                        }

                        else -> dao.getPagedListOrderUpdated(
                            params.loadSize,
                            page * params.loadSize
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