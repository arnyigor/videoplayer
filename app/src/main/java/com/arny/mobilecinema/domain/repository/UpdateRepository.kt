package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.AnwapMovie
import java.io.File

interface UpdateRepository {
    var newUpdate: String
    var updateDownloadId: Long
    var lastUpdate: String
    suspend fun downloadUpdate(): File
    fun setLastUpdate()
    fun updateMovies(movies: List<AnwapMovie>, onUpdate: (ind: Int) -> Unit)
}