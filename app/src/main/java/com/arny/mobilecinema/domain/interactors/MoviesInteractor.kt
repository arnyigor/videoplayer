package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.domain.models.AnwapMovie
import kotlinx.coroutines.flow.Flow

interface MoviesInteractor {
    val moviesPagingData: Flow<PagingData<AnwapMovie>>
}