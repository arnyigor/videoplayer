package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import kotlinx.coroutines.flow.Flow

interface MainInteractor {
    fun loadData(): Flow<DataResult<MoviesData>>
    fun getVideoPath(path: String): Flow<DataResult<String>>
}