package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.data.models.DataResult

interface JsoupRepository {
    suspend fun loadLink(path: String): DataResult<String>
}