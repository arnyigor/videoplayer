package com.arny.mobilecinema.domain.interactors.update

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import kotlinx.coroutines.flow.Flow

interface DataUpdateInteractor {
    suspend fun requestFile(force: Boolean)
    suspend fun getUpdateDate(force: Boolean): Flow<DataResult<String>>
    fun resetUpdate()
    suspend fun checkBaseUrl(): Flow<DataResult<Boolean>>
    val updateTextFlow: Flow<IWrappedString?>
}
