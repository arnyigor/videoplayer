package com.arny.mobilecinema.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DownloadFileResult
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.LoadingData
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import kotlinx.coroutines.flow.FlowCollector
import java.io.IOException
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.services.UpdateService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real service + parser + Room, with a fixture downloader and isolated database/timestamp. */
class ArchiveServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var original: UpdateRepository
    private lateinit var originalScraper: JsoupUpdateInteractor
    private var scrape: suspend (FlowCollector<DataResultWithProgress<LoadingData>>) -> Unit = {}
    private lateinit var database: AppDatabase
    private lateinit var directory: File
    private lateinit var fixture: File
    private lateinit var receiver: BroadcastReceiver
    private var originalDirectories = emptySet<String>()
    private val terminal = CountDownLatch(1)
    private val importing = CountDownLatch(1)
    private val releaseImport = CountDownLatch(1)
    private val importFinished = CountDownLatch(1)
    private val duplicateImport = CountDownLatch(1)
    private val importCalls = AtomicInteger(0)
    @Volatile private var terminalStatus: String? = null
    @Volatile private var timestamp = "unchanged"
    @Volatile private var holdImport = false

    @Before
    fun isolateSources() {
        DeviceGuard.requireApprovedDevice()
        instrumentation.runOnMainSync { context.stopService(Intent(context, UpdateService::class.java)) }
        instrumentation.waitForIdleSync()
        original = GlobalContext.get().get()
        originalScraper = GlobalContext.get().get()
        GlobalContext.get().get<CatalogUpdateTracker>().update(CatalogUpdatePhase.IDLE)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        directory = File(context.cacheDir, "archive-service-${System.nanoTime()}").apply { mkdirs() }
        fixture = File(directory, "fixture.zip")
        originalDirectories = stagingDirectories()
        val koin = GlobalContext.get()
        val real = UpdateRepositoryImpl(koin.get<ApiService>(), koin.get<JsoupService>(),
            koin.get<Prefs>(), context, database.movieDao())
        val isolated = object : UpdateRepository by real {
            override var lastUpdate: String
                get() = timestamp
                set(value) { timestamp = value }
            override fun setLastUpdate() { timestamp = "updated" }
            override fun hasLastUpdates() = false
            override suspend fun downloadFileWithProgress(url: String, fileName: String) = flow {
                emit(DataResultWithProgress.Progress(DownloadFileResult(progress = 100, size = fixture.length(), total = fixture.length())))
                emit(DataResultWithProgress.Success(DownloadFileResult(file = fixture)))
            }
            override suspend fun updateMovies(movies: List<Movie>, hasLastYearUpdate: Boolean, forceAll: Boolean, onUpdate: (Int) -> Unit) {
                if (importCalls.incrementAndGet() > 1) duplicateImport.countDown()
                try {
                    real.updateMovies(movies, hasLastYearUpdate, forceAll) { percent ->
                        if (holdImport && percent == 0) {
                            importing.countDown()
                            check(releaseImport.await(15, TimeUnit.SECONDS)) { "Import test gate timed out" }
                        }
                        onUpdate(percent)
                    }
                } finally {
                    importFinished.countDown()
                }
            }
        }
        val isolatedScraper = object : JsoupUpdateInteractor by originalScraper {
            override suspend fun parsing(flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>) = scrape(flowCollector)
            override fun stopParsing() = true
        }
        loadKoinModules(module {
            single<UpdateRepository> { isolated }
            single<JsoupUpdateInteractor> { isolatedScraper }
        })
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)
                if (status in listOf(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS,
                        AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR, AppConstants.ACTION_UPDATE_STATUS_CANCELLED)) {
                    terminalStatus = status
                    terminal.countDown()
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, IntentFilter(AppConstants.ACTION_UPDATE_STATUS))
        runBlocking(Dispatchers.IO) {
            database.movieDao().insert(MovieEntity(dbId = 7, title = "Original", pageUrl = "/films/7"))
        }
    }

    @After
    fun restoreSources() {
        releaseImport.countDown()
        if (::database.isInitialized && importCalls.get() > 0) importFinished.await(15, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { context.stopService(Intent(context, UpdateService::class.java)) }
        instrumentation.waitForIdleSync()
        if (::receiver.isInitialized) LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        if (::original.isInitialized) loadKoinModules(module { single<UpdateRepository> { original } })
        if (::originalScraper.isInitialized) loadKoinModules(module { single<JsoupUpdateInteractor> { originalScraper } })
        if (::database.isInitialized) database.close()
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    private fun stagingDirectories() = context.filesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("catalog-") }.map { it.name }.toSet()

    private fun archive(vararg files: Pair<String, String>) {
        ZipOutputStream(fixture.outputStream()).use { zip ->
            files.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun payload() = Gson().toJson(mapOf("movies" to
        (1..1000).map { Movie(title = "New $it", pageUrl = "/films/${10000 + it}") }))

    private fun start(action: String = AppConstants.ACTION_DOWNLOAD_DATABASE) {
        instrumentation.runOnMainSync {
            context.startForegroundService(Intent(context, UpdateService::class.java).apply {
                this.action = action
                putExtra(AppConstants.SERVICE_PARAM_URL, "https://example.invalid/fixture.zip")
                putExtra(AppConstants.SERVICE_PARAM_FILE, fixture.path)
                putExtra(AppConstants.SERVICE_PARAM_FORCE_ALL, true)
            })
        }
    }

    private fun awaitTerminal(expected: String) {
        assertTrue("No terminal update status", terminal.await(20, TimeUnit.SECONDS))
        assertEquals(expected, terminalStatus)
    }

    private fun assertUnchanged() = runBlocking(Dispatchers.IO) {
        assertEquals(1, database.movieDao().getCount())
        assertEquals("Original", database.movieDao().getMovie(7L)?.title)
        assertEquals("unchanged", timestamp)
    }

    private fun awaitPhase(phase: CatalogUpdatePhase) {
        val tracker = GlobalContext.get().get<CatalogUpdateTracker>()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (tracker.status.value.phase != phase && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(phase, tracker.status.value.phase)
    }

    private fun awaitCleanup() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (stagingDirectories() != originalDirectories && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertEquals("Extraction directory was not cleaned", originalDirectories, stagingDirectories())
    }

    @Test
    fun downloadedArchiveImportsAndUpdatesTimestampOnlyAfterSuccess() {
        archive("data.json" to payload())
        start()
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS)
        runBlocking(Dispatchers.IO) { assertEquals(1000, database.movieDao().getCount()) }
        assertEquals("updated", timestamp)
        assertFalse("Imported archive should be deleted", fixture.exists())
        awaitCleanup()
        val status = GlobalContext.get().get<CatalogUpdateTracker>().status.value
        assertEquals(CatalogUpdatePhase.SUCCEEDED, status.phase)
        assertEquals(1000, status.processedMovies)
    }

    @Test
    fun malformedShardRejectsWholeArchiveAndKeepsTimestamp() {
        archive("data.json" to payload(), "data_1.json" to "not JSON")
        start()
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
        assertEquals(0, importCalls.get())
        assertUnchanged()
        awaitCleanup()
        assertEquals(CatalogUpdatePhase.FAILED, GlobalContext.get().get<CatalogUpdateTracker>().status.value.phase)
    }

    @Test
    fun traversalArchiveDoesNotReachDatabase() {
        archive("data.json" to payload(), "../escaped.json" to "bad")
        start()
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
        assertEquals(0, importCalls.get())
        assertUnchanged()
        assertFalse(File(context.filesDir, "escaped.json").exists())
        awaitCleanup()
    }

    @Test
    fun cancelDuringImportRollsBackAndKeepsTimestamp() {
        holdImport = true
        archive("data.json" to payload())
        start()
        assertTrue(importing.await(15, TimeUnit.SECONDS))
        start(AppConstants.ACTION_UPDATE_ALL_CANCEL)
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_CANCELLED)
        instrumentation.waitForIdleSync()
        releaseImport.countDown()
        assertTrue(importFinished.await(15, TimeUnit.SECONDS))
        assertUnchanged()
        awaitCleanup()
        assertEquals(CatalogUpdatePhase.CANCELLED, GlobalContext.get().get<CatalogUpdateTracker>().status.value.phase)
    }

    @Test
    fun fullScraperNormalReturnCompletesWithoutSuccessEmission() {
        scrape = { collector -> collector.emit(DataResultWithProgress.Progress(LoadingData())) }
        start(AppConstants.ACTION_UPDATE_ALL)
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS)
        awaitPhase(CatalogUpdatePhase.SUCCEEDED)
    }

    @Test
    fun fullScraperContinuesAfterItemErrorAndReportsFailureOnlyAtEnd() {
        var continued = false
        scrape = { collector ->
            collector.emit(DataResultWithProgress.Error(IOException("One inaccessible card")))
            assertTrue(GlobalContext.get().get<CatalogUpdateTracker>().status.value.isRunning)
            collector.emit(DataResultWithProgress.Progress(LoadingData()))
            continued = true
        }
        start(AppConstants.ACTION_UPDATE_ALL)
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR)
        awaitPhase(CatalogUpdatePhase.FAILED)
        assertTrue(continued)
    }

    @Test
    fun repeatedLocalImportCannotStartAnotherTransaction() {
        holdImport = true
        archive("data.json" to payload())
        start(AppConstants.ACTION_UPDATE)
        assertTrue(importing.await(15, TimeUnit.SECONDS))
        start(AppConstants.ACTION_UPDATE)
        assertFalse("Repeated request started a second import", duplicateImport.await(1, TimeUnit.SECONDS))
        releaseImport.countDown()
        awaitTerminal(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS)
        assertEquals(1, importCalls.get())
        awaitCleanup()
    }
}
