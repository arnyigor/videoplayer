package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow

interface MoviesInteractor {
    fun getMovies(search: String = ""): Flow<PagingData<ViewMovie>>
    fun getMovie(id: Long): Flow<DataResult<Movie>>
}