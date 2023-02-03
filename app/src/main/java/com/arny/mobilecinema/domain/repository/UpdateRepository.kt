package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.Movie
import java.io.File

interface UpdateRepository {
    var newUpdate: String
    var updateDownloadId: Long
    var lastUpdate: String
    suspend fun downloadUpdate(): File
    fun setLastUpdate()
    fun updateMovies(movies: List<Movie>, onUpdate: (ind: Int) -> Unit)
}