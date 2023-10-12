package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.Movie
import java.io.File

interface UpdateRepository {
    var checkUpdate: Boolean
    var newUpdate: String
    var updateDownloadId: Long
    var lastUpdate: String
    var baseUrl: String
    suspend fun downloadFile(url: String, name: String): File
    fun setLastUpdate()
    fun updateMovies(movies: List<Movie>,forceAll:Boolean, onUpdate: (ind: Int) -> Unit)
    suspend fun checkBaseUrl(): Boolean
}