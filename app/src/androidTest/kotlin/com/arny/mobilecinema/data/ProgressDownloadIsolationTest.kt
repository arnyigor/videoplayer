package com.arny.mobilecinema.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.presentation.DeviceGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

class ProgressDownloadIsolationTest {
    @Test fun preparingArchiveDownloadDoesNotDeleteAnActiveMp4File() = runBlocking {
        DeviceGuard.requireApprovedDevice()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "archive-isolation-${System.nanoTime()}").apply { mkdirs() }
        // All file operations are isolated from the user's actual filesDir/database.
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        try {
            val koin = GlobalContext.get()
            val repository = UpdateRepositoryImpl(apiService = koin.get<ApiService>(), jsoup = koin.get<JsoupService>(),
                prefs = koin.get<Prefs>(), context = isolated, moviesDao = koin.get<MovieDao>())
            val movie = File(dir, "active.mp4")
            movie.outputStream().use { output ->
                output.write(byteArrayOf(1, 2, 3)); output.flush()
                // The regression deletes MP4s before returning the cold flow, before any HTTP request.
                // Do not collect: no network or production catalog import is needed to reproduce it.
                val download = repository.downloadFileWithProgress("https://example.invalid/catalog.zip", "catalog.zip")
                assertNotNull(download)
                assertTrue("Preparing ZIP update deleted the MP4 still being written", movie.exists())
                output.write(4); output.flush()
            }
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), movie.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
