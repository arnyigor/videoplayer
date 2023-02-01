package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.AnwapMovieMapper
import com.arny.mobilecinema.domain.models.AnwapMovie
import kotlinx.coroutines.delay

class MainPagingSource(
    private val dao: MovieDao,
    private val anwapMovieMapper: AnwapMovieMapper,
) : PagingSource<Int, AnwapMovie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnwapMovie> {
        val page = params.key ?: 0

        return try {
            val list = dao.getPagedList(params.loadSize, page * params.loadSize)
            val entities = list.map { anwapMovieMapper.transform(it) }
            // simulate page loading
            if (page != 0) delay(1000)
            LoadResult.Page(
                data = entities,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (entities.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AnwapMovie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}