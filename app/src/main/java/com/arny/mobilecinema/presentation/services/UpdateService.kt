package com.arny.mobilecinema.presentation.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.data.utils.unzip
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MoviesData
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.utils.getTime
import com.arny.mobilecinema.presentation.utils.sendBroadcast
import com.google.gson.GsonBuilder
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.Duration
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class UpdateService : LifecycleService(), CoroutineScope {
    private companion object {
        const val NOTICE_ID = 1001
    }

    @Inject
    lateinit var repository: UpdateRepository
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTICE_ID,
                getNotice(
                    channelId = "updating_channel_name",
                    channelName = getString(R.string.updating_channel_name),
                    title = getString(R.string.updating, 0),
                    silent = false
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                NOTICE_ID,
                getNotice(
                    channelId = "updating_channel_name",
                    channelName = getString(R.string.updating_channel_name),
                    title = getString(R.string.updating, 0),
                    silent = false
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleScope.launch(coroutineContext) {
            try {
                update(intent)
            } catch (e: Exception) {
                // TODO: Отобразить ошибку и записать
                e.printStackTrace()
                sendBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(
                        AppConstants.ACTION_UPDATE_STATUS,
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                    )
                }
                delay(1000)
                stop()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun update(intent: Intent?) {
        withContext(Dispatchers.IO) {
            val filePath = intent?.getStringExtra(AppConstants.SERVICE_PARAM_FILE)
            val forceAll =
                intent?.getBooleanExtra(AppConstants.SERVICE_PARAM_FORCE_ALL, false) ?: false
            if (filePath != null && intent.action == AppConstants.ACTION_UPDATE) {
                sendBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(
                        AppConstants.ACTION_UPDATE_STATUS,
                        AppConstants.ACTION_UPDATE_STATUS_STARTED
                    )
                }
                val file = File(filePath)
//                Timber.d("has zip file:${formatFileSize(file.length())}")
                val dataFiles = unzipData(file, context = applicationContext)
//                Timber.d("unzipData dataFiles:${dataFiles.map { "file:${it.name}:${formatFileSize(it.length())}" }}")
                if (dataFiles.isNotEmpty()) {
                    var success = false
//                    Timber.d("read files")
                    val hasUpdate = !forceAll && repository.hasLastUpdates()
                    val anwapMovies = readData(dataFiles, hasUpdate)
//                    Timber.d("read files complete:${anwapMovies.size}")
                    if (anwapMovies.isNotEmpty()) {
                        file.delete()
                        dataFiles.forEach {
                            it.delete()
                        }
                        try {
                            repository.updateMovies(
                                anwapMovies,
                                hasUpdate,
                                forceAll
                            ) { percent ->
                                updateNotification(getString(R.string.updating, percent), true)
                            }
                            repository.setLastUpdate()
                            updateNotification(
                                title = getString(R.string.update_finished_success),
                                silent = false
                            )
                            success = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            updateNotification(
                                title = getString(R.string.update_finished_error),
                                silent = false
                            )
                            success = false
                        }
                    }
                    val completeStatus = if (success) {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS
                    } else {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                    }
                    sendBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                        putString(AppConstants.ACTION_UPDATE_STATUS, completeStatus)
                    }
                    delay(3000) // wait for sending broadcast FIXME
                    stop()
                }
            } else {
                stop()
            }
        }
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            stopSelf()
        }
    }

    private fun unzipData(zipFile: File, context: Context): List<File> {
        val path = context.filesDir.path
        zipFile.unzip(path)
        val files = File(path).listFiles()?.filter { it.name.contains(".json") }
        val checkAllFiles = files?.all {
            it.isFileExists() && it.length() > 0
        }
        return if (checkAllFiles == true) {
            zipFile.delete()
            files.toList()
        } else {
            emptyList()
        }
    }

    private fun readData(files: List<File>, hasUpdateByPeriod: Boolean): List<Movie> {
        val movies = if (hasUpdateByPeriod) {
            files.find { it.name == "data_0.json" }?.let {
                readFile(it)
            } ?: getAllMovies(files)
        } else {
            files.filter { it.name == "data.json" }.flatMap {
                readFile(it)
            }
        }
        return movies
    }

    private fun getAllMovies(files: List<File>) = files.flatMap {
        readFile(it)
    }

    private fun readFile(file: File): List<Movie> = try {
        val buf = BufferedInputStream(FileInputStream(file), 32 * 1024)
        val inputStreamReader = InputStreamReader(buf, StandardCharsets.UTF_8)
        GsonBuilder()
            .setLenient()
            .create()
            .fromJson(
                inputStreamReader,
                MoviesData::class.java
            ).movies
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    private fun updateNotification(title: String, silent: Boolean) {
        (applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTICE_ID,
            getNotice("channelId", "channelName", title, silent)
        )
    }

    private fun Context.getNotice(
        channelId: String,
        channelName: String,
        title: String,
        silent: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            /* context = */ this,
            /* requestCode = */ 0,
            /* intent = */ Intent(this, MainActivity::class.java),
            /* flags = */  getFlag()
        )
        return getNotificationBuilder(channelId, channelName)
            .apply {
                setContentTitle(title)
                setAutoCancel(false)
                setSilent(silent)
                priority = NotificationCompat.PRIORITY_DEFAULT
                setSmallIcon(android.R.drawable.stat_sys_download)
                setContentIntent(contentIntent)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }.build()
    }

    private fun getFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else PendingIntent.FLAG_UPDATE_CURRENT

    private fun Context.getNotificationBuilder(
        channelId: String,
        channelName: String
    ): NotificationCompat.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(
                /* context = */ this,
                /* channelId = */ createNotificationChannel(
                    channelId = channelId,
                    channelName = channelName
                )
            )
        } else {
            NotificationCompat.Builder(this)
        }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(
        channelId: String,
        channelName: String
    ): String {
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.app_name)
                lightColor = Color.BLUE
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return channelId
    }
}