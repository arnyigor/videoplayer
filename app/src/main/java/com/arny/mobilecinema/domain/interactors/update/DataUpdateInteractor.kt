package com.arny.mobilecinema.domain.interactors.update

import com.arny.mobilecinema.data.models.DataResult
import kotlinx.coroutines.flow.Flow

interface DataUpdateInteractor {
    fun requestFile()
    suspend fun getUpdateDate(force: Boolean): Flow<DataResult<String>>
    fun resetUpdate()
    suspend fun checkBaseUrl(): Flow<DataResult<Boolean>>
}
