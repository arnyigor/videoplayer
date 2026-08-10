package com.arny.mobilecinema.data.repository.update

import android.content.Context
import android.content.Intent
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.IMovieUpdate
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DownloadFileResult
import com.arny.mobilecinema.data.models.FfmpegResult
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.data.utils.saveFileToDownloadFolder
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.utils.BufferedSharedFlow
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import com.arny.mobilecinema.presentation.utils.getTime
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.Duration
import timber.log.Timber
import java.io.File

class UpdateRepositoryImpl constructor(
    private val apiService: ApiService,
    private val jsoup: JsoupService,
    private val prefs: Prefs,
    private val context: Context,
    private val moviesDao: MovieDao
) : UpdateRepository {
    private companion object {
        const val UPDATE_PERIOD = 182L
        const val BASE_URL_PAGE_TIMEOUT_MILLIS = 3000
        const val MIN_SAFE_FULL_UPDATE_MOVIES = 1000
    }

    private val _newUrlFlow = BufferedSharedFlow<String>()
    override val newUrlFlow = _newUrlFlow.asSharedFlow()

    override var checkUpdate: Boolean = false
    override var newUpdate: String = ""
    override var updateDownloadId: Long = -1L
    override var lastUpdate: String
        get() = prefs.get<String>(PrefsConstants.LAST_DATA_UPDATE).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.LAST_DATA_UPDATE, value)
        }
    override var baseUrl: String
        get() = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.BASE_URL, value)
        }

    override suspend fun onNewUrl(url: String) {
        _newUrlFlow.emit(url)
    }

    override fun hasMovies(): Boolean = moviesDao.getCount() != 0

    /**
     * Разрешает авто-обновление базы фильмов без подтверждения пользователя:
     * - TV — всегда (мобильной сети нет, Ethernet не детектится как WIFI);
     * - телефон/планшет — только по WiFi (мобильный интернет/показывается диалог).
     */
    override fun isAutoUpdateAllowed(): Boolean {
        if (DeviceUtils.isTV(context)) return true
        return getConnectionType(context) is ConnectionType.WIFI
    }


    override fun setLastUpdate() {
        lastUpdate = newUpdate
        newUpdate = ""
    }

    override suspend fun downloadFile(url: String, name: String): File {
        val file = File(context.filesDir, name)
        file.delete()
        file.create()
        apiService.downloadFile(file, url)
        return file
    }

    override suspend fun downloadFileWithProgress(
        url: String,
        fileName: String
    ): Flow<DataResultWithProgress<DownloadFileResult>> {
        removeOldMP4Downloads()
        val file = File(context.filesDir, fileName).apply { create() }
        return apiService.downloadFileWithProgress(file, url)
    }

    override suspend fun downloadLinkWithProgress(
        url: String,
        file: File
    ): Flow<DataResultWithProgress<FfmpegResult>> {
        val cmd = "-y -i $url -c copy ${file.absolutePath}"
//        val session: FFmpegSession = FFmpegKit.execute(cmd)
//        emit(DataResultWithProgress.Success(FfmpegResult(session = session)))
        return callbackFlow {
            Timber.d("FFmpegKit cmd :$cmd, thread:${Thread.currentThread().name}")
            FFmpegKit.executeAsync(cmd, { session ->
                // CALLED WHEN SESSION IS EXECUTED
                trySend(DataResultWithProgress.Progress(FfmpegResult(session = session)))
            }, { log ->
                // CALLED WHEN SESSION PRINTS LOGS
                trySend(DataResultWithProgress.Progress(FfmpegResult(log = log)))
            }, { statistics ->
                // CALLED WHEN SESSION GENERATES STATISTICS
                trySend(DataResultWithProgress.Progress(FfmpegResult(statistics = statistics)))
            })
            awaitClose()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun removeOldMP4Downloads(): Unit = withContext(Dispatchers.IO) {
        context.filesDir.listFiles()?.let {
            for (file in it) {
                if (file.path.endsWith("mp4")) {
                    file.delete()
                }
            }
        }
    }

    override suspend fun copyFileToDownloadFolder(file: File, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            context.saveFileToDownloadFolder(file, fileName)
        }

    override suspend fun checkPath(url: String): Boolean {
        val checkPath = apiService.checkPath(url)
        return checkPath.value != 404
    }

    override fun hasLastUpdates(): Boolean {
        return if (lastUpdate.isNotBlank()) {
            val time = lastUpdate.getTime("YYYY.MM.dd HH:mm", "Europe/Moscow")
            val yearBeforeTime = DateTime.now().minus(Duration.standardDays(UPDATE_PERIOD))
            time.millis >= yearBeforeTime.millis
        } else {
            false
        }
    }

    private fun diffByGroup(
        list1: List<IMovieUpdate>,
        list2: List<IMovieUpdate>
    ): List<IMovieUpdate> {
        return (list1 + list2).groupBy { it.pageUrl }.filter { it.value.size == 1 }
            .flatMap { it.value }
    }

    override fun downloadUpdates(url: String, forceUpdate: Boolean) {
        context.sendServiceMessage(
            Intent(context.applicationContext, UpdateService::class.java),
            AppConstants.ACTION_DOWNLOAD_DATABASE
        ) {
            putString(AppConstants.SERVICE_PARAM_URL, url)
            putBoolean(AppConstants.SERVICE_PARAM_FORCE_ALL, forceUpdate)
        }
    }

    override fun updateAll() {
        context.applicationContext.sendServiceMessage(
            Intent(context.applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE_ALL
        )
    }

    override fun cancelUpdate() {
        context.applicationContext.sendServiceMessage(
            Intent(context.applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE_ALL_CANCEL
        )
    }

    override suspend fun updateMovies(
        movies: List<Movie>,
        hasLastYearUpdate: Boolean,
        forceAll: Boolean,
        onUpdate: (ind: Int) -> Unit
    ) {
        var entity = MovieEntity()
        val size = movies.size
        val currentDbCount = moviesDao.getCount()
        if (currentDbCount == 0) {
            validateFullUpdatePayloadSize(size, currentDbCount, forceAll)
            for ((ind, movie) in movies.withIndex()) {
                entity = entity.setData(movie)
                moviesDao.insert(entity)
                if (ind % 1000 == 0) {
                    onUpdate(getPercent(ind, size))
                }
            }
        } else {
            // remove movies each not in updates
            if (!hasLastYearUpdate && forceAll) {
                validateFullUpdatePayloadSize(size, currentDbCount, forceAll)
                removeNotInUpdates(movies)
            }
            val dbList = moviesDao.getUpdateMovies()
            val dbMoviesByPageUrl = dbList.groupBy { it.pageUrl }
            val moviesByTitle = movies.associateBy { it.title.lowercase() }
            var lastId = moviesDao.getLastId()

            movies.forEachIndexed { index, movie ->
                val dbMovies = dbMoviesByPageUrl[movie.pageUrl].orEmpty()
                val notCorrectDbMovies = dbMovies.filter { isEqualsUrlAndNotTitle(it, movie) }
                for (ncm in notCorrectDbMovies) {
                    entity.clear()
                    // Ищем в серверных данных фильм с тем же названием
                    val correctMovie = moviesByTitle[ncm.title.lowercase()] ?: continue

                    // Создаём новую сущность из серверного DTO и сохраняем старый dbId
                    entity = entity.setData(correctMovie).copy(dbId = ncm.dbId)

                    // Проверяем, не существует ли уже строка с таким title+pageUrl
                    val conflict = moviesDao.findByTitleAndPageUrl(entity.title, entity.pageUrl)

                    if (conflict != null && conflict.dbId != entity.dbId) {
                        moviesDao.safeUpsert(entity)
                    } else {
                        moviesDao.insert(entity)
                    }
                }
                val dbMovie = dbMovies.find { isEqualsUrlAndTitle(it, movie) }
                entity.clear()
                try {
                    val dbId = dbMovie?.dbId
                    if (dbId != null) {
                        entity = entity.setData(movie).copy(dbId = dbId)
                        moviesDao.update(entity)
                    } else {
                        lastId += 1
                        entity = entity.setData(movie).copy(dbId = lastId)
                        moviesDao.insert(entity)
                    }
                } catch (e: Exception) {
                    error("Update error for $entity has error:${e.stackTraceToString()}")
                }
                if (index % 1000 == 0) {
                    onUpdate(getPercent(index, size))
                }
            }
        }
    }

    private fun validateFullUpdatePayloadSize(
        updateSize: Int,
        currentDbCount: Int,
        forceAll: Boolean
    ) {
        if (!forceAll) return

        val minExpectedSize = maxOf(MIN_SAFE_FULL_UPDATE_MOVIES, currentDbCount / 2)
        if (updateSize < minExpectedSize) {
            error(
                "Refusing unsafe full update: update payload has $updateSize movies, " +
                        "current database has $currentDbCount movies, minimum expected is $minExpectedSize"
            )
        }
    }

    private fun removeNotInUpdates(
        moviesUpdates: List<Movie>
    ) {
        val moviesDiff = diffByGroup(moviesUpdates, moviesDao.getUpdateMovies())
        val dbMoviesNotInUpdates = moviesDiff.filterIsInstance<MovieUpdate>()
        moviesDao.deleteAll(dbMoviesNotInUpdates.map { it.dbId })
    }

    private fun isEqualsUrlAndTitle(
        it: MovieUpdate,
        movie: Movie
    ) = it.pageUrl == movie.pageUrl && it.title.equals(movie.title, true)

    private fun isEqualsUrlAndNotTitle(
        it: MovieUpdate,
        movie: Movie
    ) = it.pageUrl == movie.pageUrl && !it.title.equals(movie.title, true)

    override suspend fun checkBaseUrl(): Boolean = withContext(Dispatchers.IO) {
        val entryPointUrl = AppConstants.ENTRY_POINT_URL.trim().trimEnd('/')

        // ========================================================================
        // STRATEGY 1: Determine the actual domain from the stable entry point page.
        // The app resolves the current working mirror itself on every launch and
        // stores it, so it does not depend on build-time secrets or external files.
        // ========================================================================
        try {
            val extractedUrl = tryParseFromPage(entryPointUrl)

            baseUrl = extractedUrl
            Timber.tag("BaseUrlChecker").i("Success: URL parsed from entry point page: %s", baseUrl)
            return@withContext true
        } catch (e: Exception) {
            Timber.tag("BaseUrlChecker").w(
                e,
                "Strategy 1 failed: Could not extract from entry point page: %s",
                entryPointUrl
            )
            // Swallow exception and proceed to Strategy 2
        }

        // ========================================================================
        // STRATEGY 2: Fallback - try the build-time configured link.
        // Used only if the stable entry point dies and a fresh mirror is
        // configured in build-time secrets.
        // ========================================================================
        val buildTimeLink = BuildConfig.BASE_LINK.trim().trimEnd('/')
        if (buildTimeLink.isNotBlank() && buildTimeLink != entryPointUrl) {
            try {
                val extractedUrl = tryParseFromPage(buildTimeLink)

                baseUrl = extractedUrl
                Timber.tag("BaseUrlChecker").i("Success: URL parsed from build-time page: %s", baseUrl)
                return@withContext true
            } catch (e: Exception) {
                Timber.tag("BaseUrlChecker").w(e, "Strategy 2 failed: Could not extract from build-time page.")
            }
        }

        // ========================================================================
        // FAILURE: Both strategies failed. Keep the previously stored base URL.
        // ========================================================================
        Timber.tag("BaseUrlChecker").w("Both strategies failed to resolve base URL. Keeping saved: %s", baseUrl)
        return@withContext false
    }

    /* ------------------------------------------------------------ */
    /*  Helper Functions                                            */
    /* ------------------------------------------------------------ */

    private fun tryParseFromPage(url: String): String {
        val doc = jsoup.loadPage(url = url, timeout = BASE_URL_PAGE_TIMEOUT_MILLIS)
        val href = doc.select("ul.tl li a:contains(Фильмы)")
            .firstOrNull()
            ?.let { element -> element.absUrl("href").ifBlank { element.attr("href") } }
            ?.trimEnd('/')
            ?: throw IllegalStateException("Anchor with text 'Фильмы' not found on $url")

        val absoluteHref = when {
            href.startsWith("//") -> "https:$href"
            href.startsWith("http://", ignoreCase = true) ||
                    href.startsWith("https://", ignoreCase = true) -> href
            href.startsWith("/") -> url.trimEnd('/') + href
            else -> url.trimEnd('/') + "/" + href
        }
        return getDomainName(absoluteHref).trimEnd('/').ifBlank { url.trimEnd('/') }
    }

    private fun getPercent(ind: Int, size: Int) =
        ((ind.toDouble() / size.toDouble()) * 100).toInt()
}
