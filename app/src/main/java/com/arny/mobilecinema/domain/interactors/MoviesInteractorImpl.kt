package com.arny.mobilecinema.domain.interactors

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.AnwapMovieMapper
import com.arny.mobilecinema.domain.models.AnwapMovie
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val movieDao: MovieDao,
    private val anwapMovieMapper: AnwapMovieMapper
) : MoviesInteractor {
    override val moviesPagingData: Flow<PagingData<AnwapMovie>>
        get() = Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { MainPagingSource(movieDao, anwapMovieMapper) }.flow
}
