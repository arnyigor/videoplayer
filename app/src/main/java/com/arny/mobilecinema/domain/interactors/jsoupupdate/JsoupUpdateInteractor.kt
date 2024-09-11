package com.arny.mobilecinema.domain.interactors.jsoupupdate

import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.domain.models.LoadingData
import kotlinx.coroutines.flow.FlowCollector

interface JsoupUpdateInteractor {
    fun getDecodePlayerData(value: String): String
    suspend fun getPageData(
        url: String,
        updateToNow: Boolean,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    )

    suspend fun parsing(
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    )

    fun stopParsing(): Boolean
}