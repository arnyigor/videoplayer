package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie

class MainPagingSource(
    private val dao: MovieDao,
    private val search: String,
    private val order: String,
    private val searchType: String,
    private val searchAddTypes: List<String>,
    private val genres: List<String> = emptyList(),
    private val countries: List<String> = emptyList(),
    private val years: SimpleIntRange? = null,
    private val imdbs: SimpleFloatRange? = null,
    private val kps: SimpleFloatRange? = null,
) : PagingSource<Int, ViewMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        return try {
            val list = dao.getMovies(
                getMoviesSQL(
                    search = search,
                    order = order,
                    searchType = searchType,
                    movieTypes = getMovieTypes(),
                    limit = params.loadSize,
                    offset = page * params.loadSize,
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

    private fun getMovieTypes() = searchAddTypes.map {
        when (it) {
            AppConstants.SearchType.CINEMA -> MovieType.CINEMA
            AppConstants.SearchType.SERIAL -> MovieType.SERIAL
            else -> MovieType.NO_TYPE
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ViewMovie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}