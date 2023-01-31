package com.arny.mobilecinema.domain.repository

import java.io.File

interface UpdateRepository {
    var updateDownloadId: Long
    suspend fun downloadUpdate(): File
    fun getLastUpdate(): String
}