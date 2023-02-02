package com.arny.mobilecinema.domain.interactors

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val movieDao: MovieDao
) : MoviesInteractor {
    override fun getMovies(search: String): Flow<PagingData<ViewMovie>> =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { MainPagingSource(movieDao, search) }.flow
}
