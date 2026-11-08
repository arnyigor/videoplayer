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
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_UPDATE_TITLE
    import com.arny.mobilecinema.data.repository.AppConstants.SERVICE_PARAM_URL
    import com.arny.mobilecinema.data.utils.unzipData
    import com.arny.mobilecinema.data.utils.readCatalogFiles
    import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
    import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
    import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
    import com.arny.mobilecinema.domain.models.LoadingData
    import com.arny.mobilecinema.domain.models.Movie
    import com.arny.mobilecinema.domain.models.UpdateType
    import com.arny.mobilecinema.domain.repository.UpdateRepository
    import com.arny.mobilecinema.presentation.utils.ActivityNavigator
    import com.arny.mobilecinema.presentation.utils.DeviceUtils
    import com.arny.mobilecinema.presentation.utils.getAvailableMemory
    import com.arny.mobilecinema.presentation.utils.sendLocalBroadcast
    import kotlinx.coroutines.cancelChildren
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.ensureActive
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import timber.log.Timber
    import java.io.File
    import java.io.IOException
    import java.io.FileInputStream
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
    import kotlinx.coroutines.withTimeoutOrNull
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.currentCoroutineContext
    import kotlinx.coroutines.ensureActive
    import com.arny.mobilecinema.domain.interactors.jsoupupdate.ParsingStopException
    import kotlinx.coroutines.isActive

    /** Верхняя граница разбора одной карточки: дольше сервис держать в фоне нельзя. */
    private const val URL_UPDATE_TIMEOUT_MS = 120_000L

    internal fun normalizeUpdateUrl(
        url: String,
        entryPointBaseUrl: String,
        savedBaseUrl: String
    ): String {
        val trimmedUrl = url.trim()
        return when {
            trimmedUrl.startsWith("http://", ignoreCase = true) ||
                    trimmedUrl.startsWith("https://", ignoreCase = true) -> trimmedUrl

            trimmedUrl.startsWith("//") -> "https:$trimmedUrl"

            else -> {
                val baseUrl = getUpdateBaseUrl(entryPointBaseUrl, savedBaseUrl)
                if (baseUrl.isBlank()) {
                    throw IllegalArgumentException("Base URL is empty for relative URL: $trimmedUrl")
                }
                "$baseUrl/${trimmedUrl.trimStart('/')}"
            }
        }
    }

    internal fun getUpdateBaseUrl(entryPointBaseUrl: String, savedBaseUrl: String): String {
        val normalizedEntryPointBaseUrl = entryPointBaseUrl.trim().trimEnd('/')
        if (normalizedEntryPointBaseUrl.startsWith("http://", ignoreCase = true) ||
            normalizedEntryPointBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            return normalizedEntryPointBaseUrl
        }

        return savedBaseUrl.trim().trimEnd('/').takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
        }.orEmpty()
    }

    class UpdateService : LifecycleService(), KoinComponent {
        private companion object {
            /**
             * Unique identifier used for foreground notifications.  The ID must be unique within
             * the application; otherwise notification updates will fail silently.
             */
            const val NOTICE_ID = 100001
            private const val TAG = "UpdateService"
        }

        /**
         * Фоновое обновление карточки (запущено с экрана деталей), а не импорт ссылки.
         * Передаётся параметром в конкретный разбор: разборов может идти несколько
         * параллельно, поэтому общее поле сервиса для этого не годится.
         */
        private fun isBackgroundUpdate(intent: Intent?): Boolean =
            intent?.getBooleanExtra(AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND, false) ?: false

        /**
         * Сколько разборов по URL идёт прямо сейчас.
         *
         * Раньше первый завершившийся разбор вызывал stop(), а stop() отменяет корутины сервиса —
         * при быстром открытии нескольких карточек остальные разборы теряли и запись, и отчёт.
         */
        @Volatile
        private var activeUrlParses = 0

        /** Repository for accessing and persisting movie data. */
        private val repository: UpdateRepository by inject()

        /** Interactor used to fetch data from remote websites via Jsoup. */
        private val jsoupUpdateInteractor: JsoupUpdateInteractor by inject()
        private val catalogUpdates: CatalogUpdateTracker by inject()
        private var catalogOperation = false

        private val supervisorJob = SupervisorJob()
        @Volatile private var canceled = false
        private var downloadAllJob: Job? = null
        private var archiveJob: Job? = null

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
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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
            archiveJob?.cancel()
            if (catalogUpdates.status.value.isRunning) catalogUpdates.update(CatalogUpdatePhase.CANCELLED)
            catalogOperation = false

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
            // Свои значения на каждый запуск: параллельные разборы не должны перетирать друг друга.
            val url = intent?.getStringExtra(SERVICE_PARAM_UPDATE_URL)
            val background = isBackgroundUpdate(intent)
            activeUrlParses++
            lifecycleScope.launch {
                try {
                    sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                        putString(
                            AppConstants.ACTION_UPDATE_STATUS,
                            AppConstants.ACTION_UPDATE_STATUS_STARTED
                        )
                    }

                    getPageData(
                        url = url,
                        background = background,
                        movieTitle = intent?.getStringExtra(SERVICE_PARAM_UPDATE_TITLE).orEmpty(),
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateCompleteWithError(background = background)
                } finally {
                    activeUrlParses--
                }
            }
        }

        /**
         * Asynchronously fetches page data from the given URL using Jsoup.  The function
         * handles progress, success and error callbacks via the interactor.
         *
         * @param url URL of the remote resource to download.
         */
        private suspend fun getPageData(url: String?, background: Boolean, movieTitle: String = "") {
            if (url.isNullOrBlank()) throw DataThrowable(R.string.url_is_empty)
            val normalizedUrl = normalizeUpdateUrl(url)

            // --- 1. Парсим movieId из URL ------------------------------------
            val movieId = Regex("""/(films|serials)/(\d+)""")
                .find(normalizedUrl)
                ?.groups?.get(2)
                ?.value
                ?.toLongOrNull()
                ?: throw DataThrowable(R.string.url_is_empty)

            val caption = movieTitle.ifBlank { normalizedUrl.substringAfterLast('/') }
            var finished = false
            fun finish(success: Boolean) {
                if (finished) return
                finished = true
                lifecycleScope.launch { updateComplete(success, movieId, background) }
            }

            // Сетевой/разборочный сбой раньше оставлял сервис и спиннер деталей навсегда:
            // поток не присылал терминальное событие. Теперь есть и таймаут, и страховка ниже.
            val timedOut = withTimeoutOrNull(URL_UPDATE_TIMEOUT_MS) {
                jsoupUpdateInteractor.getPageData(normalizedUrl, true) { data ->
                    when (data) {
                        is DataResultWithProgress.Error -> {
                            if (data.throwable is CancellationException) throw data.throwable
                            notifyUpdateFailed(caption, data.throwable.localizedMessage)
                            finish(false)
                        }

                        is DataResultWithProgress.Progress -> {
                            val result = data.result
                            // 404 и подобное: понятный текст вместо «Ошибка 404» в заголовке.
                            if (result.complete && !result.success) {
                                notifyUpdateFailed(caption, result.progress[UpdateType.LINK])
                            }
                            handleProgress(result.progress, result, movieId, background)
                            // Завершение уже обработано выше — вторая попытка не нужна.
                            if (result.complete) finished = true
                        }

                        is DataResultWithProgress.Success -> finish(true)
                    }
                }
                true
            }
            if (timedOut == null) {
                notifyUpdateFailed(caption, getString(R.string.update_timeout))
                finish(false)
            } else if (!finished) {
                finish(false)
            }
        }

        /** Уведомление об ошибке обновления карточки: с названием и подсказкой, что делать. */
        private fun notifyUpdateFailed(caption: String, reason: String?) {
            updateNotification(
                title = getString(R.string.update_movie_error_title, caption),
                text = getString(
                    R.string.update_movie_error_text,
                    reason?.takeIf { it.isNotBlank() } ?: getString(R.string.update_movie_error_unknown),
                ),
                silent = false,
                addStopAction = false,
            )
        }

        private fun normalizeUpdateUrl(url: String): String =
            normalizeUpdateUrl(
                url = url,
                entryPointBaseUrl = repository.baseUrl.ifBlank { AppConstants.ENTRY_POINT_URL },
                savedBaseUrl = repository.baseUrl
            )

        private suspend fun handleProgress(
            progressMap: Map<String, String>,
            result: LoadingData,
            movieId: Long = 0L,
            background: Boolean = false,
        ) {
            for ((key, value) in progressMap.entries) {
                when (key) {
                    UpdateType.MOVIE -> {

                        val movie = progressMap[UpdateType.MOVIE]
                        if (movie != null && result.complete) {
                            // 2️⃣ основной лог – запись о завершении обновления конкретного фильма
                            Timber.tag(TAG).d("handleProgress: Movie update complete: %s", movie)
                            updateComplete(result.success, movieId, background)
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
        private suspend fun updateComplete(
            success: Boolean,
            movieId: Long = 0L,
            background: Boolean = false,
            catalog: Boolean = false,
        ) {
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
                putLong("EXTRA_MOVIE_ID", movieId)
                putBoolean(
                    AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND,
                    background
                )
            }
            delay(1000)
            if (catalog) {
                finishCatalogOperation(if (success) CatalogUpdatePhase.SUCCEEDED else CatalogUpdatePhase.FAILED)
            } else {
                stopIfNoActiveParses()
            }
        }

        /**
         * Останавливает сервис только когда другие разборы по URL уже не идут:
         * stop() отменяет корутины сервиса и убил бы их на середине.
         * Текущий разбор тоже в счётчике, поэтому порог «1».
         */
        private fun stopIfNoActiveParses() {
            if (activeUrlParses <= 1 && !catalogOperation) {
                stop()
            }
        }


        /**
         * Handles an `ACTION_DOWNLOAD_DATABASE` request.  Starts a coroutine that will
         * download the ZIP file from the given URL and then update the database.
         */
        private fun actionDownload(intent: Intent?) {
            // Do not import the same catalog concurrently on repeated manual requests.
            if (archiveJob?.isActive == true || downloadAllJob?.isActive == true) return
            catalogOperation = true
            catalogUpdates.update(CatalogUpdatePhase.DOWNLOADING)
            archiveJob = lifecycleScope.launch {
                try {
                    download(
                        intent?.getStringExtra(SERVICE_PARAM_URL),
                        intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false)
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    updateCompleteWithError(catalog = true)
                }
            }
        }

        /**
         * Handles an `ACTION_UPDATE_ALL` request.  Cancels any existing full‑update job
         * and starts a new coroutine that performs the complete update.
         */
        private fun actionDownloadAll() {
            if (archiveJob?.isActive == true || downloadAllJob?.isActive == true) return
            catalogOperation = true
            catalogUpdates.update(CatalogUpdatePhase.SCRAPING)
            downloadAllJob = lifecycleScope.launch {
                try {
                    downloadAll()
                } catch (e: Exception) {
                    // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Пропускаем системную отмену дальше!
                    if (e is CancellationException) throw e

                    e.printStackTrace()
                    ensureActive()
                    updateCompleteWithError(catalog = true)
                }
            }
        }


        /** Текст прогресса: процент и объём, если сервер отдал размер. */
        private fun formatDownloadProgress(percent: Int, downloaded: Long, total: Long): String =
            if (total > 0L) {
                "%d%% (%s / %s)".format(
                    percent,
                    android.text.format.Formatter.formatShortFileSize(applicationContext, downloaded),
                    android.text.format.Formatter.formatShortFileSize(applicationContext, total),
                )
            } else {
                android.text.format.Formatter.formatShortFileSize(applicationContext, downloaded)
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
                    var downloadedFile: File? = null
                    var downloaded = 0L
                    var total = 0L
                    updateNotification(
                        title = getString(R.string.downloading_database), text = "", silent = false,
                        indeterminate = true,
                    )
                    // The flow ends after Success/Error; import starts after the file is closed.
                    repository.downloadFileWithProgress(url, "tmp_${System.currentTimeMillis()}.zip").collect { result ->
                        when (result) {
                            is DataResultWithProgress.Progress -> {
                                catalogUpdates.update(CatalogUpdatePhase.DOWNLOADING, result.result.progress)
                                downloaded = result.result.size ?: downloaded
                                total = result.result.total ?: total
                                updateNotification(
                                    title = getString(R.string.downloading_database),
                                    text = formatDownloadProgress(result.result.progress, downloaded, total),
                                    silent = true, progress = result.result.progress,
                                    progressMax = if (total > 0) 100 else 0,
                                    indeterminate = total <= 0,
                                )
                            }
                            is DataResultWithProgress.Success -> downloadedFile = result.result.file
                            is DataResultWithProgress.Error -> throw java.io.IOException("Archive download failed", result.throwable)
                        }
                    }
                    update(
                        filePath = downloadedFile?.absolutePath ?: throw java.io.IOException("Download finished without a file"),
                        forceAll = forceAll ?: false,
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
            catalogUpdates.update(CatalogUpdatePhase.SCRAPING)
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

            var hadErrors = false
            try {
                jsoupUpdateInteractor.parsing { data ->
                    when (data) {
                        is DataResultWithProgress.Error -> {
                            if (data.throwable is CancellationException) throw data.throwable
                            hadErrors = true
                            Timber.tag(TAG).w(data.throwable, "Skipping failed catalog item")
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

                            catalogUpdates.update(CatalogUpdatePhase.SCRAPING, lastKnownPercent)
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

                        // The scraper emits per-item events; only returning completes the operation.
                        is DataResultWithProgress.Success -> Unit
                    }
                }
            } catch (_: ParsingStopException) {
                // The scraper also uses this to finish after consecutive already-current titles.
                currentCoroutineContext().ensureActive()
                if (canceled) throw CancellationException("Update cancelled")
            }
            currentCoroutineContext().ensureActive()
            updateComplete(!hadErrors, catalog = true)
        }

        /**
         * Handles an `ACTION_UPDATE` request.  Parses the local file and updates movies.
         */
        private fun actionUpdate(intent: Intent?) {
            if (archiveJob?.isActive == true || downloadAllJob?.isActive == true) return
            catalogOperation = true
            catalogUpdates.update(CatalogUpdatePhase.UNPACKING)
            archiveJob = lifecycleScope.launch {
                try {
                    update(
                        filePath = intent?.getStringExtra(SERVICE_PARAM_FILE),
                        forceAll = intent?.getBooleanExtra(SERVICE_PARAM_FORCE_ALL, false) ?: false
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    updateCompleteWithError(catalog = true)
                }
            }
        }

        /**
         * Broadcasts an error status and stops the service.
         */
        private suspend fun updateCompleteWithError(
            movieId: Long = 0L,
            background: Boolean = false,
            catalog: Boolean = false,
        ) {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Если корутину отменили, просто выходим
            if (!currentCoroutineContext().isActive) return

            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                putString(
                    AppConstants.ACTION_UPDATE_STATUS,
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                )
                putLong("EXTRA_MOVIE_ID", movieId)
                putBoolean(
                    AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND,
                    background
                )
            }
            delay(1000)
            if (catalog) finishCatalogOperation(CatalogUpdatePhase.FAILED) else stopIfNoActiveParses()
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
            // 100% referred to transfer; the database still needs unpacking, parsing and import.
            updateNotification(getString(R.string.updating_data), text = "", silent = true, indeterminate = true)
            catalogUpdates.update(CatalogUpdatePhase.UNPACKING)
            val file = File(filePath)
            if (!file.isZipArchive()) {
                Timber.tag(TAG).e("Downloaded update file is not a ZIP: %s, size=%d", file.absolutePath, file.length())
                file.delete()
                if (!repository.hasMovies()) {
                    Timber.tag(TAG).w("Database is empty, falling back to web parsing update")
                    downloadAll()
                    return
                }
                sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(AppConstants.ACTION_UPDATE_STATUS, AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
                }
                updateNotification(
                    title = getString(R.string.update_finished_error, "Invalid update archive"),
                    text = "", silent = false
                )
                finishCatalogOperation(CatalogUpdatePhase.FAILED)
                return
            }

            val dataFiles = try {
                applicationContext.unzipData(file, extension = ".json")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to unzip data file")
                sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                    putString(AppConstants.ACTION_UPDATE_STATUS, AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
                }
                updateNotification(
                    title = getString(R.string.update_finished_error, e.message ?: "Zip error"),
                    text = "", silent = false
                )
                finishCatalogOperation(CatalogUpdatePhase.FAILED)
                return@readFile
            }

            try {
                if (dataFiles.isNotEmpty()) {
                    if (forceAll && dataFiles.all { it.name == "data_0.json" }) {
                        Timber.tag(TAG).e("Refusing full update from partial archive: %s", dataFiles.map { it.name })
                        sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                            putString(AppConstants.ACTION_UPDATE_STATUS, AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
                        }
                        updateNotification(
                            title = getString(R.string.update_finished_error, "Partial archive for full update"),
                            text = "", silent = false
                        )
                        finishCatalogOperation(CatalogUpdatePhase.FAILED)
                        return@readFile
                    }

                    var success = false
                    var processedMovies: Int? = null
                    val hasUpdate = !forceAll && repository.hasLastUpdates()

                    try {
                        if (getAvailableMemory().lowMemory) {
                            throw IOException("Not enough memory to import the catalog")
                        }
                        val anwapMovies = readCatalogFiles(dataFiles, hasUpdate)
                        if (canceled) throw CancellationException("Update cancelled")
                        catalogUpdates.update(CatalogUpdatePhase.IMPORTING, 0)
                        repository.updateMovies(anwapMovies, hasUpdate, forceAll) { percent ->
                            if (canceled) throw CancellationException("Update cancelled")
                            catalogUpdates.update(CatalogUpdatePhase.IMPORTING, percent)
                            updateNotification(getString(R.string.updating, percent), text = "", silent = true,
                                progress = percent, progressMax = 100)
                            sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                                putString(AppConstants.ACTION_UPDATE_STATUS, AppConstants.ACTION_UPDATE_STATUS_PROGRESS)
                                putInt("progress_percent", percent)
                            }
                        }
                        processedMovies = anwapMovies.size
                        repository.setLastUpdate()
                        file.delete()
                        updateNotification(
                            title = getString(R.string.update_finished_success), text = "", silent = false
                        )
                        success = true
                    } catch (e: CancellationException) {
                        Timber.tag(TAG).d("Update cancelled by user")
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Catalog import failed")
                        updateNotification(
                            title = getString(R.string.update_finished_error, e.message), text = "", silent = false
                        )
                    }

                    val completeStatus = if (success) {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS
                    } else {
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR
                    }
                    sendLocalBroadcast(AppConstants.ACTION_UPDATE_STATUS) {
                        putString(AppConstants.ACTION_UPDATE_STATUS, completeStatus)
                    }
                    finishCatalogOperation(
                        if (success) CatalogUpdatePhase.SUCCEEDED else CatalogUpdatePhase.FAILED, processedMovies
                    )
                }
            } finally {
                dataFiles.firstOrNull()?.parentFile?.deleteRecursively()
            }
        }

        private suspend fun finishCatalogOperation(phase: CatalogUpdatePhase, processedMovies: Int? = null) {
            withContext(Dispatchers.Main.immediate) {
                catalogOperation = false
                catalogUpdates.update(phase, processedMovies = processedMovies)
                if (activeUrlParses == 0) stop()
            }
        }

        override fun onDestroy() {
            if (catalogOperation && catalogUpdates.status.value.isRunning) {
                catalogUpdates.update(CatalogUpdatePhase.CANCELLED)
            }
            super.onDestroy()
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

        private fun File.isZipArchive(): Boolean {
            if (!isFile || length() < 4L) return false
            return FileInputStream(this).use { stream ->
                stream.read() == 'P'.code && stream.read() == 'K'.code
            }
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
            progress: Int = 0,
            progressMax: Int = 0,
            indeterminate: Boolean = false,
        ) {
            if (!canceled) {
                getNoticeManager().notify(
                    NOTICE_ID,
                    getNotice("channelId", "channelName", title, text, addStopAction, silent, progress, progressMax, indeterminate)
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
            silent: Boolean,
            progress: Int = 0,
            progressMax: Int = 0,
            indeterminate: Boolean = false,
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
                    if (progressMax > 0) {
                        setProgress(progressMax, progress.coerceIn(0, progressMax), false)
                    } else {
                        setProgress(0, 0, indeterminate)
                    }
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