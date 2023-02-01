package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.AnwapMovie
import java.io.File

interface UpdateRepository {
    var updateDownloadId: Long
    suspend fun downloadUpdate(): File
    fun getLastUpdate(): String
    fun updateMovies(movies: List<AnwapMovie>)
}