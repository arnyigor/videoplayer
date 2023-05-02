package com.arny.mobilecinema.presentation.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.utils.DownloadHelper
import com.arny.mobilecinema.presentation.utils.sendLocalBroadcast
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.upstream.*
import dagger.android.AndroidInjection
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class MovieDownloadService : LifecycleService(), CoroutineScope {
    private companion object {
        const val NOTICE_ID = 1002
    }

    @Inject
    lateinit var playerSource: PlayerSource
    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var noticeStopped = false
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob
    private val downloadHelper = DownloadHelper()
    private val progressListener: (percent: Float, bytes: Long, startTime: Long, updateTime: Long, state: Int) -> Unit =
        { percent, bytes, _, _, state ->
            if (!noticeStopped) {
                var title = currentTitle
                val maxTitleSize = 15
                if (currentTitle.length > maxTitleSize) {
                    title = currentTitle.substring(0, maxTitleSize) + "..."
                }
                updateNotification(
                    title = getString(
                        R.string.download_cinema_title_format,
                        title,
                        percent,
                        formatFileSize(bytes, 2)
                    ),
                    text = getString(
                        R.string.download_cinema_text_format,
                        downloadHelper.getRemainTime(
                            percent = percent.toDouble(),
                        ),
                    ),
                    silent = true
                )
            }
            when (state) {
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {}
                Download.STATE_COMPLETED -> {
                    this.sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_COMPLETE) {
                        putString(AppConstants.SERVICE_PARAM_CACHE_URL, currentUrl)
                        putString(AppConstants.SERVICE_PARAM_CACHE_TITLE, currentTitle)
                    }
                    stop()
                }
                else -> stop()
            }
        }

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        startForeground(
            NOTICE_ID,
            getNotice(
                channelId = "channelId",
                channelName = "channelName",
                title = getString(R.string.download_cinema_title_format, currentTitle, 0.0f, ""),
                text = getString(R.string.download_cinema_text_format, ""),
                silent = false
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_CACHE_MOVIE -> download(intent)
            AppConstants.ACTION_CACHE_MOVIE_CANCEL -> cancelDownload()
            else -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun download(intent: Intent?) {
        val extras = intent?.extras
        downloadHelper.reset()
        noticeStopped = false
        currentUrl = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_URL).orEmpty()
        currentTitle = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_TITLE).orEmpty()
        preCacheVideo(currentUrl)
    }

    private fun cancelDownload() {
        playerSource.cancelDownload(currentUrl)
        noticeStopped = true
        stop()
    }

    private fun preCacheVideo(url: String) {
        if (url.isNotBlank()) {
            playerSource.cacheVideo(url, progressListener)
        } else {
            stop()
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

    private fun updateNotification(title: String, text: String, silent: Boolean) {
        (applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTICE_ID,
            getNotice("channelId", "channelName", title, text, silent)
        )
    }

    private fun Context.getNotice(
        channelId: String,
        channelName: String,
        title: String,
        text: String,
        silent: Boolean
    ): Notification {
        val pendingIntent: PendingIntent = PendingIntent.getService(
            /* context = */ this,
            /* requestCode = */ 0,
            /* intent = */ Intent(this, MovieDownloadService::class.java).apply {
                action = AppConstants.ACTION_CACHE_MOVIE_CANCEL
            },
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT
        )
//        val pendingIntent = PendingIntent.getBroadcast(this, 0, intentStopTrip, 0)
        val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ Intent(this, MainActivity::class.java),
                /* flags = */ PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getActivity(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ Intent(this, MainActivity::class.java),
                /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return getNotificationBuilder(channelId, channelName)
            .apply {
                setContentTitle(title)
                setContentText(text)
                setAutoCancel(false)
                setSilent(silent)
                setSmallIcon(android.R.drawable.stat_sys_download)
                setContentIntent(contentIntent)
                addAction(
                    R.drawable.ic_stop_circle,
                    getString(android.R.string.cancel),
                    pendingIntent
                )
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }.build()
    }

    private fun Context.getNotificationBuilder(
        channelId: String,
        channelName: String
    ): NotificationCompat.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(
                /* context = */ this,
                /* channelId = */ createNotificationChannel(
                    context = this,
                    channelId = channelId,
                    channelName = channelName
                )
            )
        } else {
            NotificationCompat.Builder(this)
        }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String
    ): String {
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        service?.createNotificationChannel(chan)
        return channelId
    }
}