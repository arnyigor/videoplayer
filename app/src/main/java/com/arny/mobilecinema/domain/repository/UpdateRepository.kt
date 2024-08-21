package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DownloadFileResult
import com.arny.mobilecinema.data.models.FfmpegResult
import com.arny.mobilecinema.domain.models.Movie
import kotlinx.coroutines.flow.Flow
import java.io.File

interface UpdateRepository {
    var checkUpdate: Boolean
    var newUpdate: String
    var updateDownloadId: Long
    var lastUpdate: String
    var baseUrl: String
    suspend fun downloadFile(url: String, name: String): File
    fun setLastUpdate()
    fun updateMovies(movies: List<Movie>,hasLastYearUpdate:Boolean, forceAll:Boolean, onUpdate: (ind: Int) -> Unit)
    suspend fun checkBaseUrl(): Boolean
    suspend fun checkPath(url: String): Boolean
    fun hasLastUpdates(): Boolean
    fun hasMovies(): Boolean
    fun downloadUpdates(url: String, forceUpdate: Boolean)
    fun updateDownloadCache(downloadUrl: String?, percent: Float)
    fun removeDownloadCache(downloadUrl: String?)
    suspend fun copyFileToDownloadFolder(file: File, fileName: String): Boolean
    suspend fun downloadFileWithProgress(url: String, fileName: String): Flow<DataResultWithProgress<DownloadFileResult>>
    suspend fun removeOldMP4Downloads()
    suspend fun downloadLinkWithProgress(url: String, file: File): Flow<DataResultWithProgress<FfmpegResult>>
}