package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.FavoritesDao
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.data.repository.AppConstants
import androidx.sqlite.db.SimpleSQLiteQuery

class FavoritesPagingSource(
    private val dao: FavoritesDao,
    private val search: String,
    private val order: String,
    private val searchType: String
) : PagingSource<Int, ViewMovie>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        val limit = params.loadSize
        val offset = page * limit

        // Обновляем latest_time при каждом доступе к избранному
        dao.updateLatestTime(System.currentTimeMillis())

        /* ---------------------- формируем SQL --------------------- */
        val query = buildFavoritesQuery(search, order, searchType, limit, offset)

        return try {
            val movies = dao.getFavoriteMovies(query)
            LoadResult.Page(
                data = movies,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (movies.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /* --------------------------- helper --------------------------------- */
    private fun buildFavoritesQuery(
        search: String,
        order: String,
        searchType: String,
        limit: Int,
        offset: Int
    ): SimpleSQLiteQuery {

        val sb = StringBuilder()
        sb.append("SELECT m.* FROM movies m INNER JOIN favorites f ON m.dbId=f.movie_dbid")

        // фильтр по поиску
        if (search.isNotBlank()) {
            when (searchType) {
                AppConstants.SearchType.TITLE -> sb.append(" WHERE m.title LIKE :q")
                AppConstants.SearchType.DIRECTORS -> sb.append(" WHERE m.directors LIKE :q")
                AppConstants.SearchType.ACTORS -> sb.append(" WHERE m.actors LIKE :q")
                AppConstants.SearchType.GENRES -> sb.append(" WHERE m.genres LIKE :q")
            }
        }

        // сортировка
        val orderClause = when (order) {
            AppConstants.Order.LAST_TIME -> " ORDER BY f.latest_time DESC"
            AppConstants.Order.NONE -> ""
            AppConstants.Order.TITLE -> " ORDER BY m.title COLLATE NOCASE"
            AppConstants.Order.RATINGS -> " ORDER BY m.rating DESC"
            AppConstants.Order.YEAR_DESC -> " ORDER BY m.year DESC"
            AppConstants.Order.YEAR_ASC -> " ORDER BY m.year ASC"
            else -> ""
        }
        sb.append(orderClause)

        // лимит/смещение
        sb.append(" LIMIT :limit OFFSET :offset")

        val args = mutableListOf<Any>()
        if (search.isNotBlank()) {
            args.add("%$search%")
        }
        args.add(limit)
        args.add(offset)

        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }

    override fun getRefreshKey(state: PagingState<Int, ViewMovie>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
}
