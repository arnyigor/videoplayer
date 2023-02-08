package com.arny.mobilecinema.domain.interactors.update

import com.arny.mobilecinema.data.models.DataResult

interface DataUpdateInteractor {
    fun requestFile()
    suspend fun getUpdateDate(): DataResult<String>
    suspend fun checkBaseUrl(): DataResult<Boolean>
}
