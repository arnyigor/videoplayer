package com.arny.mobilecinema.presentation.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
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
import com.arny.mobilecinema.presentation.utils.getAvailableMemory
import com.arny.mobilecinema.presentation.utils.sendLocalBroadcast
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CoroutineScope
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

class UpdateService : LifecycleService(), CoroutineScope, KoinComponent {
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

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob

    /**
     * Initializes the service.  Creates a notification channel (if required) and starts
     * the service in the foreground so that it continues to run even when the app is not in
     * the background.
     */
    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("onCreate: Service initializing")

        // Koin injection
        Timber.tag(TAG).d("onCreate: Injecting dependencies via Koin")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Timber.tag(TAG).d("onCreate: Starting foreground service with API >= Q")
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
            Timber.tag(TAG).d("onCreate: Starting foreground service with API < Q")
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
        Timber.tag(TAG).i("onCreate: Service initialized and started in foreground")
    }

    /**
     * Handles the intent that started the service.  Dispatches to the appropriate action
     * handler based on the `Intent.action` value.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).i("onStartCommand: intent.action=${intent?.action}, flags=$flags, startId=$startId")

        when (intent?.action) {
            ACTION_UPDATE -> {
                Timber.tag(TAG).d("onStartCommand: Dispatching to actionUpdate")
                actionUpdate(intent)
            }
            ACTION_UPDATE_BY_URL -> {
                Timber.tag(TAG).d("onStartCommand: Dispatching to actionUpdateByUrl")
                actionUpdateByUrl(intent)
            }
            ACTION_DOWNLOAD_DATABASE -> {
                Timber.tag(TAG).d("onStartCommand: Dispatching to actionDownload")
                actionDownload(intent)
            }
            ACTION_UPDATE_ALL -> {
                Timber.tag(TAG).d("onStartCommand: Dispatching to actionDownloadAll")
                actionDownloadAll()
            }
            ACTION_UPDATE_ALL_CANCEL -> {
                Timber.tag(TAG).d("onStartCommand: Dispatching to cancelAllAndStop")
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
        Timber.tag(TAG).i("cancelAllAndStop: Cancelling downloadAllJob and stopping service")
        downloadAllJob?.cancel()
        Timber.tag(TAG).d("cancelAllAndStop: downloadAllJob cancelled: ${downloadAllJob?.isCancelled}")
        getNoticeManager().cancel(NOTICE_ID)
        Timber.tag(TAG).d("cancelAllAndStop: Notification $NOTICE_ID cancelled")
        stop()
    }

    /**
     * Handles an `ACTION_UPDATE_BY_URL` request.  Starts a coroutine that will fetch
     * data from the specified URL and update the local database.
     */
    private fun actionUpdateByUrl(intent: Intent?) {
        Timber.tag(TAG).i("actionUpdateByUrl: Starting coroutine for URL update")
        val url = intent?.getStringExtra(SERVICE_PARAM_UPDATE_URL)
        Timber.tag(TAG).d("actionUpdateByUrl: URL parameter=$url")

        lifecycleScope.launch(coroutineContext) {
            Timber.tag(TAG).d("actionUpdateByUrl: Coroutine started")
            try {
                val url = intent?.getStringExtra(SERVICE_PARAM_UPDATE_URL)
                Timber.tag(TAG).i("actionUpdateByUrl: Calling getPageData with url=$url")
                getPageData(url)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "actionUpdateByUrl: Exception occurred")
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
        Timber.tag(TAG).d("getPageData: called with url=%s", url)

        if (!url.isNullOrBlank()) {
            // --- 1. Парсим movieId из URL ------------------------------------
            val movieId = Regex("""/(films|serials)/(\d+)""")
                .find(url)
                ?.groups?.get(2)
                ?.value
                ?.toLongOrNull()
                ?: throw DataThrowable(R.string.url_is_empty)

            importedMovieId = movieId   // сохраняем для broadcast-а
            Timber.tag(TAG).i("getPageData: Parsed movie id: %s", movieId)

            jsoupUpdateInteractor.getPageData(url, true) { data ->
                when (data) {
                    is DataResultWithProgress.Error -> {
                        val message = data.throwable.localizedMessage
                        Timber.tag(TAG).e("getPageData: Error callback: %s", message)
                        updateNotification(
                            title = getString(R.string.update_finished_error, message),
                            text = "",
                            silent = false
                        )
                        updateComplete(false)
                    }

                    is DataResultWithProgress.Progress -> {
                        val result = data.result
                        val progressMap = result.progress
                        Timber.tag(TAG).d("getPageData: Progress callback received with keys: %s", progressMap.keys.joinToString())
                        handleProgress(progressMap, result)
                    }

                    is DataResultWithProgress.Success -> {
                        Timber.tag(TAG).i("getPageData: Success callback received. Updating complete.")
                        updateComplete(true)
                    }
                }
            }
        } else {
            Timber.tag(TAG).e("getPageData: URL is empty or null")
            throw DataThrowable(R.string.url_is_empty)
        }
    }

    private suspend fun handleProgress(
        progressMap: Map<String, String>,
        result: LoadingData
    ) {
        Timber.tag(TAG).v("handleProgress: Processing progress map with %d entries", progressMap.size)

        for ((key, value) in progressMap.entries) {
            when (key) {
                UpdateType.MOVIE -> {
                    // 1️⃣ логируем вход в ветку «MOVIE» и всю карту прогресса (для отладки)
                    Timber.tag(TAG).d(
                        "handleProgress: Handling progress item: %s, full map keys=[%s]",
                        UpdateType.MOVIE,
                        progressMap.keys.joinToString()
                    )

                    val movie = progressMap[UpdateType.MOVIE]
                    if (movie != null && result.complete) {
                        // 2️⃣ основной лог – запись о завершении обновления конкретного фильма
                        Timber.tag(TAG).d("handleProgress: Movie update complete: %s", movie)

                        // 3️⃣ ещё один уровень детализации: какие флаги пришли с результатом
                        Timber.tag(TAG).i(
                            "handleProgress: Result flags for %s → complete=%b, success=%b",
                            movie,
                            result.complete,
                            result.success
                        )

                        // 4️⃣ вызов финальной функции – логируем перед этим и после (если нужно)
                        Timber.tag(TAG).v("handleProgress: Calling updateComplete(success=%b) for movie %s", result.success, movie)
                        updateComplete(result.success)

                        // 5️⃣ можно добавить «событие» в Crashlytics/Analytics, если таковой нужен
                        // FirebaseCrashlytics.getInstance()
                        //     .log("Movie updated: $movie, success=${result.success}")
                    } else {
                        Timber.tag(TAG).w(
                            "handleProgress: Skipping movie update – movie=%s, result.complete=%b",
                            movie,
                            result.complete
                        )
                    }
                }


                UpdateType.TITLE -> {
                    val title = progressMap[UpdateType.TITLE]
                    if (title != null) {
                        Timber.tag(TAG).d("handleProgress: Title progress: %s", title)
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
                        Timber.tag(TAG).d("handleProgress: Current link: %s", link)
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
        Timber.tag(TAG).i("updateComplete: Called with success=%b", success)

        if (success) {
            // Показываем финальное сообщение "Обновление успешно"
            Timber.tag(TAG).d("updateComplete: Showing success notification")
            updateNotification(
                title = getString(R.string.update_finished_success),
                text = "",
                silent = false,
                addStopAction = false
            )
            delay(1000)
        } else {
            Timber.tag(TAG).w("updateComplete: Update failed, delaying before stop")
            delay(1000)
        }

        Timber.tag(TAG).d("updateComplete: Sending local broadcast with status")
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
        Timber.tag(TAG).d("updateComplete: Broadcast sent, delaying before stop")
        delay(1000)
        Timber.tag(TAG).i("updateComplete: Calling stop()")
        stop()
    }


    /**
     * Handles an `ACTION_DOWNLOAD_DATABASE` request.  Starts a coroutine that will
     * download the ZIP file from the given URL and then update the database.
     */
    private fun actionDownload(intent: Intent?) {
        Timber.tag(TAG).i("actionDownload: Starting coroutine for database download")
        val url = intent?.getStringExtra(SERVICE_PARAM_URL)
        val forceAll = intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false)
        Timber.tag(TAG).d("actionDownload: url=%s, forceAll=%b", url, forceAll)

        lifecycleScope.launch(coroutineContext) {
            Timber.tag(TAG).d("actionDownload: Coroutine started")
            try {
                download(
                    intent?.getStringExtra(SERVICE_PARAM_URL),
                    intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false)
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "actionDownload: Exception occurred")
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
        Timber.tag(TAG).i("actionDownloadAll: Cancelling existing job and starting new one")
        downloadAllJob?.cancel()
        Timber.tag(TAG).d("actionDownloadAll: Previous job cancelled: ${downloadAllJob?.isCancelled}")

        downloadAllJob = lifecycleScope.launch(coroutineContext) {
            Timber.tag(TAG).d("actionDownloadAll: Coroutine started")
            try {
                downloadAll()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "actionDownloadAll: Exception occurred")
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
        Timber.tag(TAG).i("download: called with url=%s, forceAll=%b", url, forceAll)

        withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("download: Running on IO dispatcher")
            if (!url.isNullOrBlank()) {
                updateNotification(
                    title = getString(R.string.downloading_database),
                    text = "",
                    silent = false
                )
                Timber.tag(TAG).d("download: Starting file download from %s", url)
                val file = repository.downloadFile(url, "tmp_${System.currentTimeMillis()}.zip")
                Timber.tag(TAG).i("download: File downloaded to %s", file.absolutePath)
                update(
                    filePath = file.absolutePath,
                    forceAll = forceAll ?: false
                )
            } else {
                Timber.tag(TAG).w("download: URL is null or blank, stopping service")
                stop()
            }
        }
    }

    /**
     * Performs a full update by scraping data from the website and updating local storage.
     */
    private suspend fun downloadAll() {
        Timber.tag(TAG).i("downloadAll: Starting full update via web scraping")

        updateNotification(
            title = getString(R.string.updating_all),
            text = "",
            addStopAction = true,
            silent = false
        )

        jsoupUpdateInteractor.parsing { data ->
            when (data) {
                is DataResultWithProgress.Error -> {
                    Timber.tag(TAG).e("downloadAll: DataResultWithProgress.Error: %s", data.throwable.message)
                    updateComplete(false)
                }

                is DataResultWithProgress.Progress -> {
                    val result = data.result
                    val progress = result.progress
                    for (progressItem in progress.keys) {
                        when (progressItem) {
                            UpdateType.TITLE -> {
                                val title1 = UpdateType.TITLE
                                val title = progress[title1]
                                if (title != null) {
                                    updateNotification(
                                        title = getString(
                                            R.string.update_cinema_formatted,
                                            title
                                        ),
                                        addStopAction = true,
                                        text = "",
                                        silent = true
                                    )
                                }
                            }

                            UpdateType.LINK -> {
                                val link = progress[UpdateType.LINK]
                                if (link != null) {
                                    Timber.tag(TAG).d("downloadAll: Link progress: %s", link)
                                    updateNotification(
                                        title = link,
                                        text = "",
                                        addStopAction = true,
                                        silent = true
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }

                is DataResultWithProgress.Success -> {
                    Timber.tag(TAG).i("downloadAll: Success callback received")
                    updateComplete(true)
                }
            }
        }
    }

    /**
     * Handles an `ACTION_UPDATE` request.  Parses the local file and updates movies.
     */
    private fun actionUpdate(intent: Intent?) {
        Timber.tag(TAG).i("actionUpdate: Starting coroutine for local file update")
        val filePath = intent?.getStringExtra(SERVICE_PARAM_FILE)
        val forceAll = intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false)
        Timber.tag(TAG).d("actionUpdate: filePath=%s, forceAll=%b", filePath, forceAll)

        lifecycleScope.launch(coroutineContext) {
            Timber.tag(TAG).d("actionUpdate: Coroutine started")
            try {
                update(
                    filePath = intent?.getStringExtra(SERVICE_PARAM_FILE),
                    forceAll = intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false) ?: false
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "actionUpdate: Exception occurred")
                e.printStackTrace()
                updateCompleteWithError()
            }
        }
    }

    /**
     * Broadcasts an error status and stops the service.
     */
    private suspend fun updateCompleteWithError() {
        Timber.tag(TAG).w("updateCompleteWithError: Broadcasting error status")
        sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
            putString(
                AppConstants.ACTION_UPDATE_STATUS,
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
            )
        }
        Timber.tag(TAG).d("updateCompleteWithError: Error broadcast sent, delaying before stop")
        delay(1000)
        Timber.tag(TAG).i("updateCompleteWithError: Calling stop()")
        stop()
    }

    /**
     * Parses the given file path and initiates an update.
     *
     * @param filePath Absolute path to a ZIP file containing JSON data.
     * @param forceAll Whether all records should be updated, ignoring incremental checks.
     */
    private suspend fun update(filePath: String?, forceAll: Boolean) {
        Timber.tag(TAG).i("update: called with filePath=%s, forceAll=%b", filePath, forceAll)

        withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("update: Running on IO dispatcher")
            if (filePath != null) {
                sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(
                        AppConstants.ACTION_UPDATE_STATUS,
                        AppConstants.ACTION_UPDATE_STATUS_STARTED
                    )
                }
                Timber.tag(TAG).d("update: Broadcast STARTED sent, calling readFile")
                readFile(filePath, forceAll)
            } else {
                Timber.tag(TAG).w("update: filePath is null, stopping service")
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
        Timber.tag(TAG).i("readFile: Processing file %s with forceAll=%b", filePath, forceAll)

        val file = File(filePath)
        Timber.tag(TAG).d("readFile: File exists=%b, size=%d bytes", file.exists(), file.length())

        val dataFiles = applicationContext.unzipData(file, extension = ".json")
        Timber.tag(TAG).i("readFile: Unzipped %d JSON files", dataFiles.size)

        if (dataFiles.isNotEmpty()) {
            var success = false
            val hasUpdate = !forceAll && repository.hasLastUpdates()
            Timber.tag(TAG).d("readFile: hasUpdate=%b (forceAll=%b)", hasUpdate, forceAll)

            var anwapMovies: List<Movie> = emptyList()
            if (!getAvailableMemory().lowMemory) {
                Timber.tag(TAG).d("readFile: Memory OK, reading data files")
                anwapMovies = readData(dataFiles, hasUpdate)
                Timber.tag(TAG).i("readFile: Parsed %d movies from data files", anwapMovies.size)
            } else {
                Timber.tag(TAG).w("readFile: Low memory detected, skipping data read")
            }

            if (anwapMovies.isNotEmpty()) {
                Timber.tag(TAG).d("readFile: Deleting temp files")
                file.delete()
                dataFiles.forEach { it.delete() }

                try {
                    Timber.tag(TAG).i("readFile: Calling repository.updateMovies with %d movies", anwapMovies.size)
                    repository.updateMovies(
                        anwapMovies,
                        hasUpdate,
                        forceAll
                    ) { percent ->
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
                    Timber.tag(TAG).d("readFile: Last update timestamp saved")

                    updateNotification(
                        title = getString(R.string.update_finished_success),
                        text = "", silent = false
                    )
                    success = true
                    Timber.tag(TAG).i("readFile: Update completed successfully")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "readFile: Exception during repository update")
                    e.printStackTrace()
                    updateNotification(
                        title = getString(R.string.update_finished_error, e.message),
                        text = "", silent = false
                    )
                    success = false
                }
            } else {
                Timber.tag(TAG).w("readFile: No movies parsed, skipping repository update")
            }

            val completeStatus = if (success) {
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS
            } else {
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
            }
            Timber.tag(TAG).d("readFile: Sending broadcast with status=%s", completeStatus)
            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                putString(AppConstants.ACTION_UPDATE_STATUS, completeStatus)
            }
            Timber.tag(TAG).d("readFile: Delaying 3 seconds for broadcast delivery")
            delay(3000) // wait for sending broadcast FIXME
            Timber.tag(TAG).i("readFile: Calling stop()")
            stop()
        } else {
            Timber.tag(TAG).w("readFile: No JSON files found in archive")
        }
    }

    /**
     * Stops the service and removes the foreground notification.
     */
    private fun stop() {
        Timber.tag(TAG).i("stop: Setting canceled=true and stopping foreground service")
        canceled = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.tag(TAG).i("stop: Service stopped")
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
        Timber.tag(TAG).d("readData: Called with %d files, hasUpdateByPeriod=%b", files.size, hasUpdateByPeriod)

        val movies = if (hasUpdateByPeriod) {
            val targetFile = files.find { it.name == "data_0.json" }
            if (targetFile != null) {
                Timber.tag(TAG).d("readData: Processing incremental update from data_0.json")
                readFile(targetFile)
            } else {
                Timber.tag(TAG).w("readData: data_0.json not found, falling back to getAllMovies")
                getAllMovies(files)
            }
        } else {
            Timber.tag(TAG).d("readData: Processing full update from all files")
            val list = ArrayList<Movie>(10000)
            val gson = GsonBuilder().setLenient().create()
            for (file in files) {
                var reader: JsonReader? = null
                val data: MoviesData? = try {
                    reader = JsonReader(BufferedReader(FileReader(file)))
                    gson.fromJson(reader, MoviesData::class.java)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "readData: Error parsing file %s", file.name)
                    e.printStackTrace()
                    null
                } finally {
                    reader?.close()
                }
                if (data != null) {
                    Timber.tag(TAG).v("readData: Parsed %d movies from %s", data.movies.size, file.name)
                    list.addAll(data.movies)
                }
            }
            list
        }
        Timber.tag(TAG).i("readData: Returning %d movies", movies.size)
        return movies
    }

    /**
     * Concatenates movie lists from all JSON files.
     *
     * @param files List of extracted JSON files.
     * @return Combined list of all movies found in the provided files.
     */
    private fun getAllMovies(files: List<File>) = files.flatMap {
        val movies = readFile(it)
        Timber.tag(TAG).v("getAllMovies: Got %d movies from %s", movies.size, it.name)
        movies
    }

    /**
     * Reads a single JSON file and converts it into a list of {@link Movie} objects.
     *
     * @param file File to parse.
     * @return List of movies contained in the file or an empty list on error.
     */
    private fun readFile(file: File): List<Movie> = try {
        Timber.tag(TAG).v("readFile(single): Parsing %s (%d bytes)", file.name, file.length())
        val buf = BufferedInputStream(FileInputStream(file), 32 * 1024)
        val inputStreamReader = InputStreamReader(buf, StandardCharsets.UTF_8)
        GsonBuilder()
            .setLenient()
            .create()
            .fromJson(
                inputStreamReader,
                MoviesData::class.java
            ).movies.also { movies ->
                Timber.tag(TAG).v("readFile(single): Successfully parsed %d movies", movies.size)
            }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "readFile(single): Error parsing file %s", file.name)
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
            Timber.tag(TAG).v("updateNotification: title='%s', text='%s', silent=%b, addStopAction=%b",
                title, text, silent, addStopAction)
            getNoticeManager().notify(
                NOTICE_ID,
                getNotice("channelId", "channelName", title, text, addStopAction, silent)
            )
        } else {
            Timber.tag(TAG).w("updateNotification: Service canceled, skipping notification update")
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
            /* flags = */  FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
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
        Timber.tag(TAG).d("createNotificationChannel: Channel '%s' registered", channelId)
        return channelId
    }
}