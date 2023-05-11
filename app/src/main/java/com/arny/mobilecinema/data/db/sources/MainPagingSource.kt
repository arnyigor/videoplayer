package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SimpleSQLiteQuery
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
            val list = dao.getMovies(
                getQuery(
                    search = search,
                    order = order,
                    searchType = searchType,
                    limit = params.loadSize,
                    offset = page * params.loadSize
                )
            )
            LoadResult.Page(
                data = list,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (list.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private fun getQuery(
        search: String,
        order: String,
        searchType: String,
        limit: Int,
        offset: Int
    ): SimpleSQLiteQuery {
        val sb = StringBuilder()
        val args = mutableListOf<Any?>()
        sb.append("SELECT dbId, title, type, img, year, likes, dislikes FROM movies")
        if (search.isNotBlank()) {
            sb.append(" WHERE")
            sb.append(
                when (searchType) {
                    AppConstants.SearchType.TITLE -> " title LIKE '%' || ? || '%'"
                    AppConstants.SearchType.DIRECTORS -> " directors LIKE '%' || ? || '%'"
                    AppConstants.SearchType.ACTORS -> " actors LIKE '%' || ? || '%'"
                    AppConstants.SearchType.GENRES -> " genre LIKE '%' || ? || '%'"
                    else -> ""
                }
            )
            args.add(search)
        }
        if (order.isNotBlank()) {
            sb.append(" ORDER BY")
            sb.append(
                when (order) {
                    AppConstants.Order.NONE -> " updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC"
                    AppConstants.Order.RATINGS -> " ratingImdb DESC, ratingKp DESC, likes DESC"
                    AppConstants.Order.TITLE -> " title ASC, ratingImdb DESC, ratingKp DESC, likes DESC"
                    AppConstants.Order.YEAR_DESC -> " year DESC, ratingImdb DESC, ratingKp DESC, likes DESC"
                    AppConstants.Order.YEAR_ASC -> " year ASC, ratingImdb DESC, ratingKp DESC, likes DESC"
                    else -> ""
                }
            )
        }
        sb.append(" LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)
        sb.append(";")
        val query = sb.toString()
//        println("queryString:$query")
//        println("args:$args")
        return SimpleSQLiteQuery(query, args.toTypedArray())
    }

    override fun getRefreshKey(state: PagingState<Int, ViewMovie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}