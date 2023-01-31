package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult

interface MainInteractor {
    suspend fun getUpdateDate(): DataResult<String>
}