package com.arny.mobilecinema.domain.interactors.update

import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class DataUpdateInteractorImpl @Inject constructor(
    private val context: Context,
    private val repository: UpdateRepository
) : DataUpdateInteractor {
    private lateinit var downloadedReceiver: DownloadedReceiver

    override suspend fun checkBaseUrl(): Flow<DataResult<Boolean>> = doAsync {
        repository.checkBaseUrl()
    }

    override fun requestFile() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
        val downloadUrl: String = BuildConfig.DATA_LINK
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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
