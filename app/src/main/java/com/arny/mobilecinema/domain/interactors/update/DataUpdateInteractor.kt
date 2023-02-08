package com.arny.mobilecinema.domain.interactors.update

import com.arny.mobilecinema.data.models.DataResult
import kotlinx.coroutines.flow.Flow

interface DataUpdateInteractor {
    fun requestFile()
    suspend fun getUpdateDate(): Flow<DataResult<String>>
    suspend fun checkBaseUrl(): Flow<DataResult<Boolean>>
}
