package com.arny.mobilecinema.presentation.services


import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.FfmpegResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackInteractor
import com.arny.mobilecinema.domain.models.DownloadMovieItem
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.utils.DownloadHelper
import com.arny.mobilecinema.presentation.utils.sendLocalBroadcast
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.SessionState
import com.google.android.exoplayer2.offline.Download
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MovieDownloadService : LifecycleService(), CoroutineScope {
    private companion object {
        const val NOTICE_ID = 1002
        const val NOTICE_CHANNEL_ID = "NOTICE_CHANNEL_ID"
        const val NOTICE_CHANNEL_NAME = "NOTICE_CHANNEL_NAME"
    }

    private var fileDownloadJob: Job? = null

    @Inject
    lateinit var playerSource: PlayerSource

    @Inject
    lateinit var updateRepository: UpdateRepository

    @Inject
    lateinit var feedbackInteractor: FeedbackInteractor
    private var nextPauseResumeAction: String = ""
    private var noticeStopped = false
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob
    private val downloadHelper = DownloadHelper()
    private var downloadManager: DownloadManager? = null
    private var currentDownload: DownloadMovieItem? = null
    private var downloadList = listOf<DownloadMovieItem>()
    private var nextDuration = false
    private var curDwnldDurationMs: Long = 0L
    private var currentState = -1
    private var stTitle = ""
    private var st by Delegates.observable(-1) { _, old, new ->
        if (old != new) {
            Timber.d("State:${new.getStateString()}")
        }
    }
    private val progressListener: (
        percent: Float,
        bytes: Long,
        startTime: Long,
        updateTime: Long,
        state: Int,
        size: Int
    ) -> Unit = { percent, bytes, _, _, state, stSize ->
        st = state
        val isNoticeCanUpdate = !noticeStopped && percent >= 0.0f && bytes > 0L
        val currentDownloadListSize = downloadList.size
        val download = currentDownload
        val season = download?.season
        val episode = download?.episode
//        val downloadUrl = download?.downloadUrl
//        val curTitle = download?.title
        if (isNoticeCanUpdate) {
            var title = download?.title.orEmpty()
            val maxTitleSize = 15
            if (title.length > maxTitleSize) {
                title = title.substring(0, maxTitleSize) + "..."
            }
            if (season != 0 && episode != 0) {
                title += String.format(
                    "(%s%s,%s%s)",
                    season,
                    getString(R.string.spinner_season),
                    episode,
                    getString(R.string.spinner_episode)
                )
            }
            stTitle = title
        }
        currentState = state
        val sizeDiff = stSize - currentDownloadListSize
//        Timber.d("progressListener ${state.getStateString()} size:$currentDownloadListSize, s:$stSize, sizeDiff:$sizeDiff, bytes:${bytes.formatSize()}")
        when {
            isNoticeCanUpdate && state == Download.STATE_QUEUED -> {
                updateNotification(
                    title = getString(
                        R.string.download_cinema_title_format,
                        stTitle,
                        percent,
                    ),
                    text = getString(R.string.download_cinema_state_state_pause),
                    silent = false
                )
            }

            isNoticeCanUpdate && state == Download.STATE_REMOVING && sizeDiff > 1 -> {
                updateNotification(
                    title = getString(
                        R.string.download_cinema_title_format,
                        stTitle,
                        percent,
                    ),
                    text = getString(
                        R.string.download_cinema_state_state_updating,
                    ),
                    silent = false
                )
            }

            isNoticeCanUpdate -> {
                val titleRes = if (downloadList.isNotEmpty()) {
                    R.string.download_text_format_one_more
                } else {
                    R.string.download_text_format
                }
                updateNotification(
                    title = getString(
                        R.string.download_cinema_title_format,
                        stTitle,
                        percent,
                    ),
                    text = getString(
                        titleRes,
                        downloadHelper.getRemainTime(percent.toDouble()),
                        resources.getQuantityString(
                            R.plurals.downloads,
                            currentDownloadListSize,
                            currentDownloadListSize
                        )
                    ),
                    silent = true
                )
            }

            else -> {}
        }
        when (state) {
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_STOPPED -> {
                updateDownloadedCache(percent)
                sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_UPDATE) {
                    putString(
                        AppConstants.SERVICE_PARAM_CACHE_MOVIE_PAGE_URL,
                        download?.pageUrl.orEmpty()
                    )
                    putFloat(AppConstants.SERVICE_PARAM_PERCENT, percent)
                    putLong(AppConstants.SERVICE_PARAM_BYTES, bytes)
                    if (season != null && episode != null) {
                        putInt(AppConstants.SERVICE_PARAM_CACHE_SEASON, season)
                        putInt(AppConstants.SERVICE_PARAM_CACHE_EPISODE, episode)
                    }
                }
            }

            Download.STATE_COMPLETED -> {
                updateDownloadedCache(percent)
                sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_COMPLETE)
                if (stSize == 0) {
                    startNextDownload()
                }
            }

            Download.STATE_REMOVING -> {
            }

            else -> {
                if (stSize == 0) {
                    stopCurrentService()
                }
            }
        }
    }

    private fun updateDownloadedCache(percent: Float) {
        if (percent > 0) {
            playerSource.updateDownloadCache(currentDownload?.downloadUrl, percent)
        }
    }

    private fun startNextDownload() {
        if (downloadList.isNotEmpty()) {
            val downloadMovieItem = downloadList[0]
            currentDownload = downloadMovieItem.copy()
            downloadHelper.reset()
            playerSource.cacheVideo(
                videoUrl = currentDownload?.downloadUrl.orEmpty(),
                currentTitle = currentDownload?.title.orEmpty()
            )
            removeFirstDownload()
        } else {
            stopCurrentService()
        }
    }

    private fun removeFirstDownload() {
        downloadList = downloadList.toMutableList().apply {
            removeAt(0)
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
        initNotice()
        playerSource.setListener(progressListener)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private fun initNotice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTICE_ID,
                getNotice(
                    channelId = "channelId",
                    channelName = "channelName",
                    title = getString(
                        R.string.download_cinema_title_format,
                        currentDownload?.title.orEmpty(),
                        0.0f
                    ),
                    text = getString(R.string.download_cinema_text_format_empty_downloads, ""),
                    silent = false,
                    addUpdateActions = false
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                NOTICE_ID,
                getNotice(
                    channelId = "channelId",
                    channelName = "channelName",
                    title = getString(
                        R.string.download_cinema_title_format,
                        currentDownload?.title.orEmpty(),
                        0.0f
                    ),
                    text = getString(R.string.download_cinema_text_format_empty_downloads, ""),
                    silent = false,
                    addUpdateActions = false
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_DOWNLOAD_FILE -> downloadFile(intent)
            AppConstants.ACTION_TEST_FILE -> testFile(intent)
            AppConstants.ACTION_CACHE_MOVIE -> download(intent)
            AppConstants.ACTION_CACHE_MOVIE_CANCEL -> cancelDownload()
            AppConstants.ACTION_CACHE_MOVIE_EXIT -> exitDownload()
            AppConstants.ACTION_CACHE_MOVIE_PAUSE -> pauseDownload()
            AppConstants.ACTION_CACHE_MOVIE_RESUME -> resumeDownload()
            AppConstants.ACTION_CACHE_MOVIE_SKIP -> skipDownload()
            else -> stopCurrentService()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun testFile(intent: Intent?) {
        val extras = intent?.extras
        val url = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_URL).orEmpty()
        val fileName = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_FILENAME).orEmpty()
        val title = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_TITLE).orEmpty()
        fileDownloadJob = lifecycleScope.launch(coroutineContext) {
            updateNotification(
                title = getString(R.string.downloading_filename, title),
                text = getString(
                    R.string.downloading_filename,
                    fileName
                ),
                silent = false,
                false
            )
            downloadHelper.reset()
            nextDuration = false
            curDwnldDurationMs = 0L
            val file = File(filesDir, fileName)
            updateRepository.downloadLinkWithProgress(url, file).collectLatest { test ->
                when (test) {
                    is DataResultWithProgress.Error -> {
                        val stackTraceToString = test.throwable.stackTraceToString()
                        Timber.e("Error:$stackTraceToString")
                        Toast.makeText(applicationContext, stackTraceToString, Toast.LENGTH_SHORT)
                            .show()
                        stopCurrentService()
                    }

                    is DataResultWithProgress.Progress -> {
                        showFfmpegResult(test.result, title)
                    }

                    is DataResultWithProgress.Success -> {
                        showFfmpegResult(test.result, title)
                        updateNotification(
                            title = getString(R.string.saving_to_download_file),
                            text = fileName,
                            silent = false,
                            false
                        )
                        saveAndClose(file)
                    }
                }
            }
        }
    }

    private fun showFfmpegResult(result: FfmpegResult, title: String) {
        when {
            result.result != null -> {
                Timber.d("result:${result.result}")
            }

            result.cmd != null -> {
                Timber.d("cmd:$result")
            }

            result.log != null -> {
                val log = result.log
                when (log.level) {
                    com.arthenica.ffmpegkit.Level.AV_LOG_INFO -> {
                        initDuration(log)
                    }

                    else -> {}
                }
                Timber.d("Log: sessionId:${log.sessionId}, level:${log.level}, message:${log.message}")
            }

            result.session != null -> {
                val session = result.session
                val state = session.state
                val returnCode = session.returnCode
                val format = String.format(
                    "FFmpeg process exited with state %s and rc %s.%s",
                    state.getFfmpegSessionString(),
                    returnCode,
                    session.failStackTrace
                )
                Timber.d("Session: $format")
            }

            result.statistics != null -> {
                val statistics = result.statistics
                if (curDwnldDurationMs != 0L) {
                    val percent = (statistics.time * 100f) / curDwnldDurationMs
                    updateNotification(
                        title = getString(R.string.downloading_filename, title),
                        text = getString(
                            R.string.downloading_progress_percent_only,
                            getString(
                                R.string.download_text_format,
                                downloadHelper.getRemainTime(percent)
                            )
                        ),
                        silent = true,
                        false
                    )
                }
                Timber.d(
                    "Statistics: sessionId:${statistics.sessionId} size:${statistics.size}," +
                            " time:${statistics.time}, speed:${statistics.speed}, bitrate:${statistics.bitrate}," +
                            " frame:${statistics.videoFrameNumber}, fps:${statistics.videoFps}, quality:${statistics.videoQuality}"
                )
            }
        }
    }

    private fun initDuration(log: Log) {
        val message = log.message
        when {
            curDwnldDurationMs == 0L && !nextDuration && message.contains("Duration:") -> {
                nextDuration = true
            }

            nextDuration && message.contains(":") -> {
                // Log: sessionId:1, level:AV_LOG_INFO, message:  Duration:
//                  Log: sessionId:1, level:AV_LOG_INFO, message:00:10:00.46
                nextDuration = false
                val durationArr = message.split(":")
                val hrsMs =
                    durationArr[0].toLong().toDuration(DurationUnit.HOURS).inWholeMilliseconds
                val minsMs =
                    durationArr[1].toLong().toDuration(DurationUnit.HOURS).inWholeMilliseconds
                val secMs = (durationArr[2].toFloat() * 1000f).toLong()
                    .toDuration(DurationUnit.MILLISECONDS).inWholeMilliseconds
                curDwnldDurationMs = hrsMs + minsMs + secMs
                Timber.d("curDwnldDurationMs:$curDwnldDurationMs")
            }
        }
    }

    private fun SessionState?.getFfmpegSessionString(): String {
        return when (this) {
            SessionState.CREATED -> "CREATED"
            SessionState.RUNNING -> "RUNNING"
            SessionState.FAILED -> "FAILED"
            SessionState.COMPLETED -> "COMPLETED"
            null -> "NULL"
        }
    }

    private fun downloadFile(intent: Intent?) {
        val extras = intent?.extras
        val url = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_URL).orEmpty()
        val fileName = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_FILENAME).orEmpty()
        val title = extras?.getString(AppConstants.SERVICE_PARAM_DOWNLOAD_TITLE).orEmpty()
        fileDownloadJob = lifecycleScope.launch(coroutineContext) {
            updateNotification(
                title = getString(R.string.downloading_filename, title),
                text = getString(
                    R.string.downloading_filename,
                    fileName
                ),
                silent = false,
                false
            )
            downloadHelper.reset()
            updateRepository.downloadFileWithProgress(url, fileName).collectLatest { download ->
                when (download) {
                    is DataResultWithProgress.Error -> {
                        download.throwable.cause?.printStackTrace()
                        val message = download.throwable.message ?: download.throwable.cause?.let {
                            getFullError(
                                it,
                                applicationContext
                            )
                        }.orEmpty()
                        feedbackInteractor.setLastError("$message; $title; $url")
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        stopCurrentService()
                    }

                    is DataResultWithProgress.Progress -> {
                        val progress =
                            downloadHelper.getRemainTime(download.result.progress.toDouble())
                        updateNotification(
                            title = getString(R.string.downloading_filename, title),
                            text = getString(
                                R.string.downloading_progress,
                                formatFileSize(download.result.size ?: 0L),
                                formatFileSize(download.result.total ?: 0L),
                                download.result.progress.toString(),
                                getString(R.string.download_text_format, progress)
                            ),
                            silent = true,
                            false
                        )
                    }

                    is DataResultWithProgress.Success -> {
                        updateNotification(
                            title = getString(R.string.saving_to_download_file),
                            text = fileName,
                            silent = false,
                            false
                        )
                        saveAndClose(download.result.file)
                    }
                }
            }
        }
    }

    private suspend fun saveAndClose(file: File?) {
        if (file != null) {
            val copy = updateRepository.copyFileToDownloadFolder(
                file,
                file.name
            )
            if (copy) {
                updateRepository.removeOldMP4Downloads()
            }
            Toast.makeText(
                applicationContext,
                getString(R.string.saved_downloaded_file),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                applicationContext,
                getString(R.string.download_error_file_is_empty),
                Toast.LENGTH_SHORT
            ).show()
        }
        sendLocalBroadcast(AppConstants.ACTION_DOWNLOAD_FILE_COMPLETE)
        stopCurrentService()
    }

    private fun download(intent: Intent?) {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_PAUSE
        noticeStopped = false
        val extras = intent?.extras
        val cacheUrl = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_URL).orEmpty()
        val pageUrl = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_MOVIE_PAGE_URL).orEmpty()
        val cacheTitle = extras?.getString(AppConstants.SERVICE_PARAM_CACHE_TITLE).orEmpty()
        val cacheSeason = extras?.getInt(AppConstants.SERVICE_PARAM_CACHE_SEASON) ?: 0
        val cacheEpisode = extras?.getInt(AppConstants.SERVICE_PARAM_CACHE_EPISODE) ?: 0
        addToDownloadList(
            DownloadMovieItem(
                pageUrl = pageUrl,
                downloadUrl = cacheUrl,
                title = cacheTitle,
                season = cacheSeason,
                episode = cacheEpisode
            )
        )
        if (currentState != Download.STATE_QUEUED
            && currentState != Download.STATE_DOWNLOADING && currentState != Download.STATE_STOPPED
        ) {
            startNextDownload()
        }
    }

    private fun addToDownloadList(download: DownloadMovieItem) {
        downloadList = downloadList.toMutableList().apply { add(download) }
    }

    private fun cancelDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_RESUME
        playerSource.cancelDownload(currentDownload?.downloadUrl.orEmpty())
        downloadList = downloadList.toMutableList().apply {
            clear()
        }
        noticeStopped = true
        sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_COMPLETE)
        stopCurrentService()
    }

    private fun exitDownload() {
        downloadList = downloadList.toMutableList().apply {
            clear()
        }
        noticeStopped = true
        sendLocalBroadcast(AppConstants.ACTION_CACHE_VIDEO_COMPLETE)
        stopCurrentService()
    }

    private fun pauseDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_RESUME
        playerSource.pauseDownload()
    }

    private fun resumeDownload() {
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_PAUSE
        playerSource.resumeDownloads()
    }

    private fun skipDownload() {
        val isCurrentActionPause = nextPauseResumeAction == AppConstants.ACTION_CACHE_MOVIE_RESUME
        val download = currentDownload?.copy()
        val hasDownloads = downloadList.isNotEmpty()
        nextPauseResumeAction = AppConstants.ACTION_CACHE_MOVIE_PAUSE
        playerSource.pauseDownload()
        if (!isCurrentActionPause) {
            removeCachedDownload()
            playerSource.skipDownload(currentDownload?.downloadUrl.orEmpty())
        }
        startNextDownload()
        if (isCurrentActionPause && download != null && hasDownloads) {
            addToDownloadList(download)
        }
    }

    private fun removeCachedDownload() {
        playerSource.removeDownloadCache(currentDownload?.downloadUrl)
    }

    private fun stopCurrentService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            stopSelf()
        }
        playerSource.removeListener()
        fileDownloadJob?.cancel(CancellationException("Service Stopped"))
    }

    private fun updateNotification(
        title: String,
        text: String,
        silent: Boolean,
        addUpdateActions: Boolean = true
    ) {
        (applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTICE_ID,
            getNotice(NOTICE_CHANNEL_ID, NOTICE_CHANNEL_NAME, title, text, silent, addUpdateActions)
        )
    }

    private fun Context.getNotice(
        channelId: String,
        channelName: String,
        title: String,
        text: String,
        silent: Boolean,
        addUpdateActions: Boolean = true
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
        val skipIntent: PendingIntent = PendingIntent.getService(
            /* context = */ this,
            /* requestCode = */ 0,
            /* intent = */ Intent(this, MovieDownloadService::class.java).apply {
                action = AppConstants.ACTION_CACHE_MOVIE_SKIP
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
                if (addUpdateActions) {
                    addAction(
                        R.drawable.ic_skip_next,
                        getString(R.string.download_skip),
                        skipIntent
                    )
                    addAction(
                        getPauseResumeIcon(nextPauseResumeAction),
                        getPauseResumeBtnTitle(nextPauseResumeAction),
                        pauseResumeIntent
                    )
                }
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