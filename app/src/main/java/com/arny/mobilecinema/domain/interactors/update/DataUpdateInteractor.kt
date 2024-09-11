package com.arny.mobilecinema.domain.interactors.update

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.DataUpdateResult
import kotlinx.coroutines.flow.Flow

interface DataUpdateInteractor {
    suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean)
    suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>>
    fun resetUpdate()
    suspend fun checkBaseUrl(): Flow<DataResult<Boolean>>
    fun updateAll()
}
