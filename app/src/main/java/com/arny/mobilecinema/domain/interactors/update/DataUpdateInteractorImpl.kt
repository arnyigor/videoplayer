package com.arny.mobilecinema.domain.interactors.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DataUpdateInteractorImpl @Inject constructor(
    private val context: Context,
    private val repository: UpdateRepository
) : DataUpdateInteractor {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var forceUpdate = false
    private val _updateFlow = MutableStateFlow<IWrappedString?>(null)
    override val updateTextFlow: Flow<IWrappedString?> = _updateFlow.asStateFlow()
    private val _errorFlow = MutableStateFlow<IWrappedString?>(null)
    override val errorFlow: Flow<IWrappedString?> = _errorFlow.asStateFlow()

    private fun forceUpdate(context: Context?, id: Long) {
        val downloadManager =
            context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
        val uri = downloadManager?.getUriForDownloadedFile(id)
        if (uri != null && context != null) {
            FilePathUtils.getPath(uri, context)?.let {
                update(File(it), forceUpdate)
            }
        }
    }

    override suspend fun checkBaseUrl(): Flow<DataResult<Boolean>> = doAsync {
        repository.checkBaseUrl()
    }

    override suspend fun requestFile(force: Boolean) {
        this.forceUpdate = force
        var updateString = ResourceString(R.string.downloading_database)
        _updateFlow.value = updateString
        try {
            val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
            val dataLink =
                if (BuildConfig.DEBUG) BuildConfig.DATA_DEBUG_LINK else BuildConfig.DATA_LINK
            val description = DownloadManager.Request(Uri.parse(dataLink))
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setTitle(zipFile.name)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDescription(context.getString(R.string.wating))
            val request = description
                .setDestinationInExternalFilesDir(
                    /* context = */ context,
                    /* dirType = */ Environment.DIRECTORY_DOWNLOADS,
                    /* subPath = */ File.separator + zipFile
                )
            var downloadId: Long
            downloadManager.enqueue(request).apply {
                downloadId = this
                repository.updateDownloadId = downloadId
            }
            updateDownloadStatus(downloadId)
        } catch (e: SecurityException) {
            e.printStackTrace()
            updateString = ResourceString(R.string.downloading_database_error_unsupport_path)
            _errorFlow.value = updateString
        } catch (e: DataThrowable) {
            e.printStackTrace()
            updateString = ResourceString(e.errorRes)
            _errorFlow.value = updateString
        }
        _updateFlow.value = updateString
    }

    override fun resetUpdate() {
        repository.newUpdate = ""
        repository.checkUpdate = false
    }

    override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<String>> = doAsync {
        if (force) {
            repository.newUpdate = ""
            repository.checkUpdate = false
        }
        var newUpdate = ""
        if (!repository.checkUpdate && repository.newUpdate.isBlank()) {
            repository.checkUpdate = true
            val updateFile = repository.downloadFile(
                if (BuildConfig.DEBUG) BuildConfig.UPDATE_DEBUG_LINK else BuildConfig.UPDATE_LINK,
                AppConstants.UPDATE_FILE
            )
            newUpdate = updateFile.readText()
            updateFile.delete()
        }
        if (repository.lastUpdate != newUpdate && newUpdate.isNotBlank()) {
            repository.newUpdate = newUpdate
            newUpdate
        } else {
            ""
        }
    }

    private suspend fun updateDownloadStatus(downloadId: Long) {
        when (isDownloadManagerComplete(downloadId)) {
            DownloadMangerStatus.PROGRESS -> {
                delay(1000)
                updateDownloadStatus(downloadId)
            }

            DownloadMangerStatus.ERROR -> {
                throw DataThrowable(R.string.download_manager_fail)
            }

            DownloadMangerStatus.COMPLETE -> {
                forceUpdate(context, downloadId)
            }
        }
    }

    private fun isDownloadManagerComplete(downloadId: Long): DownloadMangerStatus {
        val c: Cursor? = try {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        return when {
            c == null -> DownloadMangerStatus.ERROR
            c.moveToFirst() -> {
                val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = c.getInt(statusIndex)
//                Timber.d("Download status [${status.toStringStatus()}]")
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadMangerStatus.COMPLETE
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> DownloadMangerStatus.PROGRESS

                    DownloadManager.STATUS_FAILED -> {
                        // val reasonIndex = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                        // val reason = c.getInt(reasonIndex)
                        //  Timber.d("Download not correct, status [${status.toStringStatus()}] reason [$reason]")
                        DownloadMangerStatus.ERROR
                    }

                    else -> DownloadMangerStatus.PROGRESS
                }
            }

            else -> DownloadMangerStatus.PROGRESS
        }
    }

    /*private fun Int.toStringStatus(): String {
        return when (this) {
            DownloadManager.STATUS_SUCCESSFUL -> "STATUS_SUCCESSFUL"
            DownloadManager.STATUS_FAILED -> "STATUS_FAILED"
            DownloadManager.STATUS_PAUSED -> "STATUS_PAUSED"
            DownloadManager.STATUS_PENDING -> "STATUS_PENDING"
            DownloadManager.STATUS_RUNNING -> "STATUS_RUNNING"
            else -> "STATUS_NONE"
        }
    }*/

    private fun update(file: File, forceUpdate: Boolean) {
        context.sendServiceMessage(
            Intent(context.applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE
        ) {
            putString(AppConstants.SERVICE_PARAM_FILE, file.path)
            putBoolean(AppConstants.SERVICE_PARAM_FORCE_ALL, forceUpdate)
        }
    }
}
