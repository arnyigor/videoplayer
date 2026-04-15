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
    import com.arny.mobilecinema.data.models.DataResultWithProgress
    import com.arny.mobilecinema.data.models.DataThrowable
    import com.arny.mobilecinema.data.repository.AppConstants
    import com.arny.mobilecinema.data.repository.AppConstants.ACTION_DOWNLOAD_DATABASE
    import com.arny.mobilecinema.data.repository.AppConstants.ACTION_UPDATE
    import com.arny.mobilecinema.data.repository.AppConstants.ACTION_UPDATE_ALL
    import com.arny.mobilecinema.data.repository.AppConstants.ACTION_UPDATE_ALL_CANCEL
    import com.arny.mobilecinema.data.repository.AppConstants.ACTION_UPDATE_BY_URL
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_FILE
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_FORCE_ALL
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_UPDATE_URL
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_URL
    import com.arny.mobilecinema.data.utils.unzipData
    import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
    import com.arny.mobilecinema.domain.models.LoadingData
    import com.arny.mobilecinema.domain.models.Movie
    import com.arny.mobilecinema.domain.models.MoviesData
    import com.arny.mobilecinema.domain.models.UpdateType
    import com.arny.mobilecinema.domain.repository.UpdateRepository
    import com.arny.mobilecinema.presentation.utils.ActivityNavigator
    import com.arny.mobilecinema.presentation.utils.DeviceUtils
    import com.arny.mobilecinema.presentation.utils.getAvailableMemory
    import com.arny.mobilecinema.presentation.utils.sendLocalBroadcast
    import com.google.gson.GsonBuilder
    import com.google.gson.stream.JsonReader
    import kotlinx.coroutines.cancelChildren
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.ensureActive
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import timber.log.Timber
    import java.io.BufferedInputStream
    import java.io.BufferedReader
    import java.io.File
    import java.io.FileInputStream
    import java.io.FileReader
    import java.io.InputStreamReader
    import java.nio.charset.StandardCharsets
    import kotlin.coroutines.CoroutineContext

    /**
     * Service responsible for downloading, parsing and updating the local movie database.
     *
     * The service runs in the foreground to avoid being killed by the system. It supports several
     * actions:
     * - `ACTION_UPDATE` – update from a local file.
     * - `ACTION_DOWNLOAD_DATABASE` – download a ZIP file containing JSON data and update it.
     * - `ACTION_UPDATE_ALL` – perform a full update via web scraping.
     * - `ACTION_UPDATE_BY_URL` – update from an arbitrary URL using Jsoup.
     * - `ACTION_UPDATE_ALL_CANCEL` – cancel an ongoing full‑update operation.
     *
     * All operations are executed inside coroutines, making use of the AndroidX Lifecycle
     * components to automatically cancel jobs when the service is destroyed.
     */
    import org.koin.core.component.KoinComponent
    import org.koin.core.component.inject

    import kotlinx.coroutines.CancellationException
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.currentCoroutineContext
    import kotlinx.coroutines.isActive

    class UpdateService : LifecycleService(), KoinComponent {
        private companion object {
            /**
             * Unique identifier used for foreground notifications.  The ID must be unique within
             * the application; otherwise notification updates will fail silently.
             */
            const val NOTICE_ID = 100001
            private const val TAG = "UpdateService"
        }

        @Volatile
        private var importedMovieId = 0L

        /** Repository for accessing and persisting movie data. */
        private val repository: UpdateRepository by inject()

        /** Interactor used to fetch data from remote websites via Jsoup. */
        private val jsoupUpdateInteractor: JsoupUpdateInteractor by inject()

        private val supervisorJob = SupervisorJob()
        private var canceled = false
        private var downloadAllJob: Job? = null

        // Хранение последнего известного прогресса
        private var lastKnownCurrent: Int = -1
        private var lastKnownTotal: Int = -1
        private var lastKnownPercent: Int = -1
        private var lastKnownTitleForUi: String? = null

        /**
         * Initializes the service.  Creates a notification channel (if required) and starts
         * the service in the foreground so that it continues to run even when the app is not in
         * the background.
         */
        override fun onCreate() {
            super.onCreate()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTICE_ID,
                    getNotice(
                        addStopAction = true,
                        channelId = "channelId",
                        channelName = "channelName",
                        title = getString(R.string.updating_all),
                        text = "",
                        silent = true
                    ),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(
                    NOTICE_ID,
                    getNotice(
                        addStopAction = true,
                        channelId = "channelId",
                        channelName = "channelName",
                        title = getString(R.string.updating_all),
                        text = "",
                        silent = true
                    ),
                )
            }
        }

        /**
         * Handles the intent that started the service.  Dispatches to the appropriate action
         * handler based on the `Intent.action` value.
         */
        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            when (intent?.action) {
                ACTION_UPDATE -> {
                    actionUpdate(intent)
                }
                ACTION_UPDATE_BY_URL -> {
                    actionUpdateByUrl(intent)
                }
                ACTION_DOWNLOAD_DATABASE -> {
                    actionDownload(intent)
                }
                ACTION_UPDATE_ALL -> {
                    actionDownloadAll()
                }
                ACTION_UPDATE_ALL_CANCEL -> {
                    cancelAllAndStop()
                }
                else -> {
                    Timber.tag(TAG).w("onStartCommand: Unknown action=${intent?.action}")
                }
            }
            return super.onStartCommand(intent, flags, startId)
        }

        /**
         * Cancels a full‑update operation and stops the service.
         */
        private fun cancelAllAndStop() {
            downloadAllJob?.cancel()

            // Send broadcast for TV UI to know that update was cancelled
            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                putString(
                    AppConstants.ACTION_UPDATE_STATUS,
                    AppConstants.ACTION_UPDATE_STATUS_CANCELLED
                )
            }

            getNoticeManager().cancel(NOTICE_ID)
            stop()
        }

        /**
         * Handles an `ACTION_UPDATE_BY_URL` request.  Starts a coroutine that will fetch
         * data from the specified URL and update the local database.
         */
        private fun actionUpdateByUrl(intent: Intent?) {
            lifecycleScope.launch {
                try {
                    sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                        putString(
                            AppConstants.ACTION_UPDATE_STATUS,
                            AppConstants.ACTION_UPDATE_STATUS_STARTED
                        )
                    }

                    val url = intent?.getStringExtra(SERVICE_PARAM_UPDATE_URL)
                    getPageData(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateCompleteWithError()
                }
            }
        }

        /**
         * Asynchronously fetches page data from the given URL using Jsoup.  The function
         * handles progress, success and error callbacks via the interactor.
         *
         * @param url URL of the remote resource to download.
         */
        private suspend fun getPageData(url: String?) {
            if (!url.isNullOrBlank()) {
                // --- 1. Парсим movieId из URL ------------------------------------
                val movieId = Regex("""/(films|serials)/(\d+)""")
                    .find(url)
                    ?.groups?.get(2)
                    ?.value
                    ?.toLongOrNull()
                    ?: throw DataThrowable(R.string.url_is_empty)

                importedMovieId = movieId   // сохраняем для broadcast-а

                jsoupUpdateInteractor.getPageData(url, true) { data ->
                    when (data) {
                        is DataResultWithProgress.Error -> {
                            val message = data.throwable.localizedMessage
                            updateNotification(
                                title = getString(R.string.update_finished_error, message),
                                text = "",
                                silent = false
                            )
                            if (data.throwable is CancellationException) return@getPageData

                            updateComplete(false)
                        }

                        is DataResultWithProgress.Progress -> {
                            val result = data.result
                            val progressMap = result.progress
                            handleProgress(progressMap, result)
                        }

                        is DataResultWithProgress.Success -> {
                            updateComplete(true)
                        }
                    }
                }
            } else {
                throw DataThrowable(R.string.url_is_empty)
            }
        }

        private suspend fun handleProgress(
            progressMap: Map<String, String>,
            result: LoadingData
        ) {
            for ((key, value) in progressMap.entries) {
                when (key) {
                    UpdateType.MOVIE -> {

                        val movie = progressMap[UpdateType.MOVIE]
                        if (movie != null && result.complete) {
                            // 2️⃣ основной лог – запись о завершении обновления конкретного фильма
                            Timber.tag(TAG).d("handleProgress: Movie update complete: %s", movie)
                            updateComplete(result.success)
                        }
                    }


                    UpdateType.TITLE -> {
                        val title = progressMap[UpdateType.TITLE]
                        if (title != null) {
                            updateNotification(
                                title = getString(
                                    R.string.update_cinema_formatted,
                                    title
                                ),
                                text = "",
                                silent = false,
                                addStopAction = true
                            )
                        }
                    }

                    UpdateType.PAGE_CURRENT_LINK -> {
                        val link = progressMap[UpdateType.PAGE_CURRENT_LINK]
                        if (link != null) {
                            updateNotification(
                                title = "",
                                text = link,
                                silent = true,
                                addStopAction = true
                            )
                        }
                    }

                    // Новые ключи, которые ранее не учитывались
                    UpdateType.URL -> Timber.tag(TAG).d("handleProgress: URL progress (ignored) – %s", value)
                    UpdateType.LINK -> Timber.tag(TAG).d("handleProgress: Link progress (ignored) – %s", value)
                    else -> Timber.tag(TAG).w("handleProgress: Unknown progress item: %s", key)
                }
            }
        }


        /**
         * Finalizes the update process by broadcasting status and stopping the service.
         *
         * @param success `true` if the update succeeded, otherwise `false`.
         */
        private suspend fun updateComplete(success: Boolean) {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Если корутину отменили, просто выходим
            if (!currentCoroutineContext().isActive) return

            if (success) {
                // Показываем финальное сообщение "Обновление успешно"
                updateNotification(
                    title = getString(R.string.update_finished_success),
                    text = "",
                    silent = false,
                    addStopAction = false
                )
                delay(1000)
            } else {
                delay(1000)
            }

            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                putString(
                    AppConstants.ACTION_UPDATE_STATUS,
                    if (success) {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS
                    } else {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                    }
                )
                putLong("EXTRA_MOVIE_ID", importedMovieId)
            }
            delay(1000)
            stop()
        }


        /**
         * Handles an `ACTION_DOWNLOAD_DATABASE` request.  Starts a coroutine that will
         * download the ZIP file from the given URL and then update the database.
         */
        private fun actionDownload(intent: Intent?) {
            lifecycleScope.launch {
                try {
                    download(
                        intent?.getStringExtra(SERVICE_PARAM_URL),
                        intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateCompleteWithError()
                }
            }
        }

        /**
         * Handles an `ACTION_UPDATE_ALL` request.  Cancels any existing full‑update job
         * and starts a new coroutine that performs the complete update.
         */
        private fun actionDownloadAll() {
            downloadAllJob?.cancel()

            downloadAllJob = lifecycleScope.launch {
                try {
                    downloadAll()
                } catch (e: Exception) {
                    // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Пропускаем системную отмену дальше!
                    if (e is CancellationException) throw e

                    e.printStackTrace()
                    ensureActive()
                    updateCompleteWithError()
                }
            }
        }

        /**
         * Downloads a ZIP file from the given URL and initiates an update with its contents.
         *
         * @param url URL of the ZIP file to download.
         * @param forceAll Whether all records should be updated, ignoring incremental checks.
         */
        private suspend fun download(url: String?, forceAll: Boolean?) {

            withContext(Dispatchers.IO) {
                if (!url.isNullOrBlank()) {
                    updateNotification(
                        title = getString(R.string.downloading_database),
                        text = "",
                        silent = false
                    )
                    val file = repository.downloadFile(url, "tmp_${System.currentTimeMillis()}.zip")
                    update(
                        filePath = file.absolutePath,
                        forceAll = forceAll ?: false
                    )
                } else {
                    stop()
                }
            }
        }

        /**
         * Performs a full update by scraping data from the website and updating local storage.
         */
        private suspend fun downloadAll() {
            // Сброс прогресса перед новым обновлением
            lastKnownCurrent = -1
            lastKnownTotal = -1
            lastKnownPercent = -1
            lastKnownTitleForUi = null

            val isTv = DeviceUtils.isTV(applicationContext)
            val silent = !isTv

            updateNotification(
                title = getString(R.string.updating_all),
                text = "",
                addStopAction = true,
                silent = silent
            )

            jsoupUpdateInteractor.parsing { data ->
                when (data) {
                    is DataResultWithProgress.Error -> {
                        updateComplete(false)
                    }

                    is DataResultWithProgress.Progress -> {
                        val result = data.result
                        val progress = result.progress

                        val currentRaw = progress[UpdateType.CURRENT_INDEX]?.toIntOrNull()
                        val totalRaw = progress[UpdateType.TOTAL_COUNT]?.toIntOrNull()

                        // Обновляем только если есть новые данные
                        if (currentRaw != null) {
                            lastKnownCurrent = currentRaw
                        }
                        if (totalRaw != null && totalRaw > 0) {
                            lastKnownTotal = totalRaw
                        }

                        // Вычисляем процент только если есть валидные данные
                        if (lastKnownCurrent >= 0 && lastKnownTotal > 0) {
                            lastKnownPercent = ((lastKnownCurrent * 100f) / lastKnownTotal).toInt().coerceIn(0, 100)
                        }

                        for (progressItem in progress.keys) {
                            when (progressItem) {
                                UpdateType.TITLE -> {
                                    val title = progress[UpdateType.TITLE]
                                    if (!title.isNullOrBlank()) {
                                        lastKnownTitleForUi = title
                                        updateNotification(
                                            title = getString(
                                                R.string.update_cinema_formatted,
                                                title
                                            ),
                                            addStopAction = true,
                                            text = "",
                                            silent = silent
                                        )
                                    }
                                }

                                UpdateType.LINK -> {
                                    val link = progress[UpdateType.LINK]
                                    if (!link.isNullOrBlank() && lastKnownTitleForUi.isNullOrBlank()) {
                                        lastKnownTitleForUi = link
                                        updateNotification(
                                            title = link,
                                            text = "",
                                            addStopAction = true,
                                            silent = silent
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }

                        // Send broadcast for TV UI progress
                        sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                            putString(
                                AppConstants.ACTION_UPDATE_STATUS,
                                AppConstants.ACTION_UPDATE_STATUS_PROGRESS
                            )
                            putString(
                                "update_title",
                                lastKnownTitleForUi ?: getString(R.string.updating_all)
                            )
                            // Используем сохранённый прогресс вместо вычисленного из текущего события
                            putInt("progress_percent", lastKnownPercent)
                            putInt("progress_current", lastKnownCurrent)
                            putInt("progress_total", lastKnownTotal)
                        }
                    }

                    is DataResultWithProgress.Success -> {
                        updateComplete(true)
                    }
                }
            }
        }

        /**
         * Handles an `ACTION_UPDATE` request.  Parses the local file and updates movies.
         */
        private fun actionUpdate(intent: Intent?) {
            lifecycleScope.launch {
                try {
                    update(
                        filePath = intent?.getStringExtra(SERVICE_PARAM_FILE),
                        forceAll = intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false) ?: false
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateCompleteWithError()
                }
            }
        }

        /**
         * Broadcasts an error status and stops the service.
         */
        private suspend fun updateCompleteWithError() {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Если корутину отменили, просто выходим
            if (!currentCoroutineContext().isActive) return

            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                putString(
                    AppConstants.ACTION_UPDATE_STATUS,
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                )
            }
            delay(1000)
            stop()
        }

        /**
         * Parses the given file path and initiates an update.
         *
         * @param filePath Absolute path to a ZIP file containing JSON data.
         * @param forceAll Whether all records should be updated, ignoring incremental checks.
         */
        private suspend fun update(filePath: String?, forceAll: Boolean) {
            withContext(Dispatchers.IO) {
                if (filePath != null) {
                    sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                        putString(
                            AppConstants.ACTION_UPDATE_STATUS,
                            AppConstants.ACTION_UPDATE_STATUS_STARTED
                        )
                    }
                    readFile(filePath, forceAll)
                } else {
                    stop()
                }
            }
        }

        /**
         * Reads and processes all JSON files extracted from the ZIP archive.
         *
         * @param filePath Path to the ZIP archive.
         * @param forceAll Whether a full update should be performed.
         */
        private suspend fun readFile(filePath: String, forceAll: Boolean) {
            val file = File(filePath)
            val dataFiles = applicationContext.unzipData(file, extension = ".json")

            if (dataFiles.isNotEmpty()) {
                var success = false
                val hasUpdate = !forceAll && repository.hasLastUpdates()

                var anwapMovies: List<Movie> = emptyList()
                if (!getAvailableMemory().lowMemory) {
                    anwapMovies = readData(dataFiles, hasUpdate)
                }

                if (anwapMovies.isNotEmpty()) {
                    file.delete()
                    dataFiles.forEach { it.delete() }

                    try {
                        repository.updateMovies(
                            anwapMovies,
                            hasUpdate,
                            forceAll
                        ) { percent ->
                            // Прерываем работу, если сервис был отменен
                            if (canceled) throw CancellationException("Update cancelled")

                            updateNotification(getString(R.string.updating, percent), text = "", true)
                            // Broadcast progress for TV UI
                            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                                putString(
                                    AppConstants.ACTION_UPDATE_STATUS,
                                    AppConstants.ACTION_UPDATE_STATUS_PROGRESS
                                )
                                putInt("progress_percent", percent)
                            }
                        }
                        repository.setLastUpdate()

                        updateNotification(
                            title = getString(R.string.update_finished_success),
                            text = "", silent = false
                        )
                        success = true
                    } catch (e: Exception) {
                        // Если это отмена - не показываем сообщение об ошибке
                        if (e is CancellationException) {
                            Timber.tag(TAG).d("Update cancelled by user")
                            return@readFile
                        }
                        e.printStackTrace()
                        updateNotification(
                            title = getString(R.string.update_finished_error, e.message),
                            text = "", silent = false
                        )
                        success = false
                    }
                }

                val completeStatus = if (success) {
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS
                } else {
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                }
                sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(AppConstants.ACTION_UPDATE_STATUS, completeStatus)
                }
                delay(3000) // wait for sending broadcast FIXME
                stop()
            }
        }

        /**
         * Stops the service and removes the foreground notification.
         */
        private fun stop() {
            canceled = true
            supervisorJob.cancelChildren() // Отменяем все активные корутины сервиса
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        /**
         * Reads a list of JSON files and converts them into a flat list of {@link Movie} objects.
         *
         * @param files List of extracted JSON files.
         * @param hasUpdateByPeriod If `true`, only the file named `data_0.json` is processed,
         * otherwise all files are merged together.
         * @return List of movies parsed from the given files.
         */
        private fun readData(files: List<File>, hasUpdateByPeriod: Boolean): List<Movie> {
            val movies = if (hasUpdateByPeriod) {
                val targetFile = files.find { it.name == "data_0.json" }
                if (targetFile != null) {
                    readFile(targetFile)
                } else {
                    getAllMovies(files)
                }
            } else {
                val list = ArrayList<Movie>(10000)
                val gson = GsonBuilder().setLenient().create()
                for (file in files) {
                    var reader: JsonReader? = null
                    val data: MoviesData? = try {
                        reader = JsonReader(BufferedReader(FileReader(file)))
                        gson.fromJson(reader, MoviesData::class.java)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    } finally {
                        reader?.close()
                    }
                    if (data != null) {
                        list.addAll(data.movies)
                    }
                }
                list
            }
            return movies
        }

        /**
         * Concatenates movie lists from all JSON files.
         *
         * @param files List of extracted JSON files.
         * @return Combined list of all movies found in the provided files.
         */
        private fun getAllMovies(files: List<File>) = files.flatMap {
            readFile(it)
        }

        /**
         * Reads a single JSON file and converts it into a list of {@link Movie} objects.
         *
         * @param file File to parse.
         * @return List of movies contained in the file or an empty list on error.
         */
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

        /**
         * Builds and displays a notification representing the current state of an update.
         *
         * @param title Title shown in the notification header.
         * @param text Body text displayed under the title.
         * @param silent Whether the notification should be silent (no sound/vibration).
         * @param addStopAction If `true`, adds a "Cancel" button that stops an ongoing full update.
         */
        private fun updateNotification(
            title: String,
            text: String,
            silent: Boolean,
            addStopAction: Boolean = false,
        ) {
            if (!canceled) {
                getNoticeManager().notify(
                    NOTICE_ID,
                    getNotice("channelId", "channelName", title, text, addStopAction, silent)
                )
            }
        }

        /**
         * Retrieves the system notification manager.
         *
         * @return Instance of {@link NotificationManager}.
         */
        private fun getNoticeManager() =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        /**
         * Constructs a [Notification] with optional stop action and sound settings.
         *
         * @param channelId ID of the notification channel to use.
         * @param channelName Name of the notification channel (used only on API < 26).
         * @param title Title of the notification.
         * @param text Body text of the notification.
         * @param addStopAction Whether to include a "Cancel" action button.
         * @param silent If `true`, the notification will not play sound or vibrate.
         * @return Fully built [Notification] instance ready for display.
         */
        private fun Context.getNotice(
            channelId: String,
            channelName: String,
            title: String,
            text: String,
            addStopAction: Boolean = false,
            silent: Boolean
        ): Notification {
            val contentIntent = PendingIntent.getActivity(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ ActivityNavigator.getMainActivityIntent(this),
                /* flags = */  PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val pendingFlags: Int =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val stopIntent: PendingIntent = PendingIntent.getService(
                /* context = */ this,
                /* requestCode = */ 0,
                /* intent = */ Intent(this, UpdateService::class.java).apply {
                    action = ACTION_UPDATE_ALL_CANCEL
                },
                /* flags = */ pendingFlags
            )
            return getNotificationBuilder(channelId, channelName)
                .apply {
                    setContentTitle(title)
                    if (text.isNotBlank()) {
                        setContentText(text)
                    }
                    setContentText(text)
                    setAutoCancel(false)
                    setSilent(silent)
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    setSmallIcon(android.R.drawable.stat_sys_download)
                    setContentIntent(contentIntent)
                    if (addStopAction) {
                        addAction(
                            R.drawable.ic_stop_circle,
                            getString(android.R.string.cancel),
                            stopIntent
                        )
                    }
                    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                }.build()
        }

        /**
         * Creates a [NotificationCompat.Builder] using the supplied channel ID and name.
         *
         * @param channelId Identifier of the notification channel.
         * @param channelName Name of the channel (used on API < 26).
         * @return Builder ready to be configured further by the caller.
         */
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

        /**
         * Creates a notification channel for API 26+ and registers it with the system.
         *
         * @param channelId Unique identifier of the channel.
         * @param channelName Human‑readable name shown in device settings.
         * @return The same `channelId` supplied, facilitating inline usage.
         */
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
            val notificationManager: NotificationManager =  getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
    }