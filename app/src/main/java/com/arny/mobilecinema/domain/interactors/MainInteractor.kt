package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.AnwapMovie

interface MainInteractor {
    suspend fun downloadData(): DataResult<List<AnwapMovie>>
}