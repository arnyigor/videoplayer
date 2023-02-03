package com.arny.mobilecinema.domain.interactors

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val movieDao: MovieDao,
    private val movieMapper: MovieMapper,
) : MoviesInteractor {
    override fun getMovies(search: String): Flow<PagingData<ViewMovie>> =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { MainPagingSource(movieDao, search.trim()) }.flow

    override fun getMovie(id: Long): Flow<DataResult<Movie>> = doAsync {
        val entity = movieDao.getMovie(id) ?: throw DataThrowable(R.string.movie_not_found)
        movieMapper.transform(entity)
    }
}
