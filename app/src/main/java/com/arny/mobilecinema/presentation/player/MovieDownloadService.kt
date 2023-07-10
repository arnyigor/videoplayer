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
    private var resetDownloads: Boolean = false
    private var nextPauseResumeAction: String = ""
    private var noticeStopped = false
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob
    private val downloadHelper = DownloadHelper()
    private val progressListener: (
        percent: Float,
        bytes: Long,
        startTime: Long,
        updateTime: Long,
        state: Int,
        size: Int
    ) -> Unit = { percent, bytes, _, _, state, size ->
        if (!noticeStopped && percent >= 0.0f && bytes > 0L) {
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
                    formatFileSize(bytes, 1)
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
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_STOPPED -> {
                sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_UPDATE) {
                    putFloat(AppConstants.SERVICE_PARAM_PERCENT, percent)
                    putLong(AppConstants.SERVICE_PARAM_BYTES, bytes)
                }
            }

            Download.STATE_COMPLETED -> {
                sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_COMPLETE) {
                    putString(AppConstants.SERVICE_PARAM_CACHE_URL, currentUrl)
                    putString(AppConstants.SERVICE_PARAM_CACHE_TITLE, currentTitle)
                }
                if (size == 0) {
                    stop()
                }
            }

            Download.STATE_REMOVING -> {}
            else -> {
                if (size == 0) {
                    stop()
                }
            }
        }
    }

    private fun Int?.getStateString(): String {
        return if (this != null) {
            when (this) {
                Download.STATE_QUEUED -> "STATE_QUEUED"
                Download.STATE_STOPPED -> "STATE_STOPPED"
                Download.STATE_DOWNLOADING -> "STATE_DOWNLOADING"
                Download.STATE_COMPLETED -> "STATE_COMPLETED"
                Download.STATE_FAILED -> "STATE_FAILED"
                Download.STATE_REMOVING -> "STATE_REMOVING"
                Download.STATE_RESTARTING -> "STATE_RESTARTING"
                else -> "UNKNOWN"
            }
        } else {
            "null"
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
        playerSource.setListener(progressListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_CACHE_MOVIE -> download(intent)
            AppConstants.ACTION_CACHE_MOVIE_CANCEL -> cancelDownload()
            AppConstants.ACTION_CACHE_MOVIE_EXIT -> exitDownload()
            AppConstants.ACTION_CACHE_MOVIE_PAUSE -> pauseDownload()
            AppConstants.ACTION_CACHE_MOVIE_RESUME -> resumeDownload()
            else -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun download(intent: Intent?) {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_PAUSE
        val extras = intent?.extras
        downloadHelper.reset()
        noticeStopped = false
        currentUrl = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_URL).orEmpty()
        currentTitle = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_TITLE).orEmpty()
        resetDownloads =
            extras?.getBoolean(AppConstants.SERVICE_PARAM_RESET_CURRENT_DOWNLOADS) == true
        preCacheVideo()
    }

    private fun cancelDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_RESUME
        playerSource.cancelDownload(currentUrl)
        noticeStopped = true
        stop()
    }

    private fun exitDownload() {
        noticeStopped = true
        stop()
    }

    private fun pauseDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_RESUME
        playerSource.pauseDownload()
    }

    private fun resumeDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_PAUSE
        playerSource.resumeDownloads()
    }

    private fun preCacheVideo() {
        if (currentUrl.isNotBlank()) {
            playerSource.cacheVideo(currentUrl, currentTitle)
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
        playerSource.removeListener()
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
        val isNextPause = nextPauseResumeAction == AppConstants.ACTION_CACHE_MOVIE_PAUSE
        val pendingFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopIntent: PendingIntent = PendingIntent.getService(
            /* context = */ this,
            /* requestCode = */ 0,
            /* intent = */ Intent(this, MovieDownloadService::class.java).apply {
                action =
                    if (!isNextPause) AppConstants.ACTION_CACHE_MOVIE_EXIT else AppConstants.ACTION_CACHE_MOVIE_CANCEL
            },
            /* flags = */ pendingFlags
        )
        val pauseResumeIntent: PendingIntent = PendingIntent.getService(
            /* context = */ this,
            /* requestCode = */ 0,
            /* intent = */ Intent(this, MovieDownloadService::class.java).apply {
                action = nextPauseResumeAction
            },
            /* flags = */ pendingFlags
        )
        val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ Intent(this, MainActivity::class.java),
                /* flags = */ pendingFlags
            )
        } else {
            PendingIntent.getActivity(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ Intent(this, MainActivity::class.java),
                /* flags = */ pendingFlags
            )
        }
        return getNotificationBuilder(channelId, channelName)
            .apply {
                setContentTitle(title)
                setContentText(text)
                setOngoing(!isNextPause)
                setAutoCancel(!isNextPause)
                setSilent(silent)
                setSmallIcon(
                    if (isNextPause) {
                        android.R.drawable.stat_sys_download
                    } else {
                        android.R.drawable.ic_media_pause
                    }
                )
                setContentIntent(contentIntent)
                addAction(
                    R.drawable.ic_stop_circle,
                    getString(
                        if (isNextPause) {
                            android.R.string.cancel
                        } else {
                            R.string.exit
                        }
                    ),
                    stopIntent
                )
                addAction(
                    getPauseResumeIcon(nextPauseResumeAction),
                    getPauseResumeBtnTitle(nextPauseResumeAction),
                    pauseResumeIntent
                )
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }.build()
    }

    private fun getPauseResumeBtnTitle(action: String): String =
        getString(
            when (action) {
                AppConstants.ACTION_CACHE_MOVIE_PAUSE -> R.string.pause_download
                AppConstants.ACTION_CACHE_MOVIE_RESUME -> R.string.resume_download
                else -> R.string.pause_download
            }
        )

    private fun getPauseResumeIcon(action: String): Int =
        when (action) {
            AppConstants.ACTION_CACHE_MOVIE_PAUSE -> R.drawable.ic_pause_circle_outline
            AppConstants.ACTION_CACHE_MOVIE_RESUME -> R.drawable.ic_play_circle_outline_gray
            else -> R.drawable.ic_pause_circle_outline
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