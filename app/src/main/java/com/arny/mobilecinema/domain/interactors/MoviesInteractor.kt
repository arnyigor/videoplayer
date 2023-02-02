package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow

interface MoviesInteractor {
    fun getMovies(search: String = ""): Flow<PagingData<ViewMovie>>
}