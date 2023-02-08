package com.arny.mobilecinema.domain.interactors.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DataUpdateInteractorImpl @Inject constructor(
    private val context: Context,
    private val repository: UpdateRepository
) : DataUpdateInteractor {
    private lateinit var downloadedReceiver: DownloadedReceiver

    override suspend fun checkBaseUrl(): Flow<DataResult<Boolean>> = doAsync {
        when {
            repository.checkBaseUrl() -> true
            else -> {
                repository.createNewBaseUrl()
                true
            }
        }
    }

    override fun requestFile() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
        val downloadUrl: String = BuildConfig.data_link
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

    override suspend fun getUpdateDate(): Flow<DataResult<String>> = doAsync {
        var newUpdate = ""
        if (repository.newUpdate.isBlank()) {
            val updateFile = withContext(Dispatchers.IO) { repository.downloadUpdate() }
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
        val intent = Intent(context, UpdateService::class.java).apply {
            putExtra("file", file.path)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
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
                            val file = File(it)
                            println("получен file:$file, с размером ${formatFileSize(file.length())}")
                            update(file)
                        }
                    }
                }
            }
        }
    }
}
