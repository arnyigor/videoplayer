package com.arny.mobilecinema.domain.interactors.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.update.UpdateService
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

class DataUpdateInteractorImpl @Inject constructor(
    private val context: Context,
    private val repository: UpdateRepository
) : DataUpdateInteractor {
    private val _updateFlow = MutableStateFlow<IWrappedString?>(null)
    override val updateTextFlow: Flow<IWrappedString?> = _updateFlow.asStateFlow()
    private lateinit var downloadedReceiver: DownloadedReceiver

    override suspend fun checkBaseUrl(): Flow<DataResult<Boolean>> = doAsync {
        repository.checkBaseUrl()
    }

    override suspend fun requestFile() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
        val request = DownloadManager.Request(Uri.parse(BuildConfig.DATA_LINK))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setTitle(zipFile.name)
            .setDescription(context.getString(R.string.downloading_update))
            .setDescription(context.getString(R.string.wating))
            .setDestinationInExternalFilesDir(
                /* context = */ context,
                /* dirType = */ Environment.DIRECTORY_DOWNLOADS,
                /* subPath = */ File.separator + zipFile
            )
        observeUpdateState()
        downloadManager.enqueue(request).apply {
            repository.updateDownloadId = this
        }
        _updateFlow.value = ResourceString(R.string.downloading_database)
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
                BuildConfig.UPDATE_LINK,
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

   private fun observeUpdateState() {
        downloadedReceiver = DownloadedReceiver()
        context.registerReceiver(
            downloadedReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        )
    }

    private fun update(file: File) {
        context.sendServiceMessage(
            Intent(context.applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE
        ) {
            putString(AppConstants.SERVICE_PARAM_FILE, file.path)
        }
    }

    inner class DownloadedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { _ ->
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (repository.updateDownloadId == id) {
                    context?.unregisterReceiver(this)
                    val downloadManager =
                        context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
                    val uri = downloadManager?.getUriForDownloadedFile(id)
                    if (uri != null && context != null) {
                        FilePathUtils.getPath(uri, context)?.let {
                            update(File(it))
                        }
                    }
                }
            }
        }
    }
}
