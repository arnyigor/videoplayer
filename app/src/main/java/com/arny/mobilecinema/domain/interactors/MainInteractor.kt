package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.domain.models.AnwapMovie
import kotlinx.coroutines.flow.Flow

interface MainInteractor {
    fun loadData(): Flow<DataResult<MoviesData>>
    suspend fun loadDb(): DataResult<List<AnwapMovie>>
    fun getVideoPath(path: String): Flow<DataResult<String>>
}