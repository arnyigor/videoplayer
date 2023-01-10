package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MockData
import com.arny.mobilecinema.data.network.responses.MoviesData
import kotlinx.coroutines.flow.Flow

interface GistsRepository {
    fun loadData(): Flow<DataResult<MoviesData>>
    fun getMockData(): List<MockData?>
}