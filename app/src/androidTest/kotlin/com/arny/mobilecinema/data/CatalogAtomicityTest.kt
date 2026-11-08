package com.arny.mobilecinema.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.data.db.models.FavoriteEntity
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.presentation.DeviceGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Every test uses a private in-memory database, never the application's catalog. */
class CatalogAtomicityTest {
    private suspend fun withRepository(block: suspend (AppDatabase, UpdateRepositoryImpl) -> Unit) {
        DeviceGuard.requireApprovedDevice()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val client = KtorClient().client
        try {
            val repository = UpdateRepositoryImpl(
                ApiService(client), JsoupService.getInstance(), Prefs.getInstance(context), context, database.movieDao()
            )
            block(database, repository)
        } finally {
            database.close()
            client.close()
        }
    }

    private fun payload() = (1..1000).map { Movie(title = "New $it", pageUrl = "/films/${10000 + it}") }

    @Test
    fun failedFullImportRollsBackRemovalsAndInserts() = runBlocking(Dispatchers.IO) {
        withRepository { database, repository ->
            val dao = database.movieDao()
            dao.insert(MovieEntity(dbId = 7, title = "Original", pageUrl = "/films/7"))
            val before = dao.getUpdateMovies()
            try {
                repository.updateMovies(payload(), hasLastYearUpdate = false, forceAll = true) {
                    throw IOException("Injected failure after removal and first insert")
                }
                fail("Expected injected failure")
            } catch (_: IOException) {
                assertEquals(before, dao.getUpdateMovies())
                assertEquals(1, dao.getCount())
            }
        }
    }

    @Test
    fun cancelledFullImportRollsBackRemovalsAndInserts() = runBlocking(Dispatchers.IO) {
        withRepository { database, repository ->
            val dao = database.movieDao()
            dao.insert(MovieEntity(dbId = 7, title = "Original", pageUrl = "/films/7"))
            val before = dao.getUpdateMovies()
            try {
                repository.updateMovies(payload(), hasLastYearUpdate = false, forceAll = true) {
                    throw CancellationException("Injected cancellation")
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(before, dao.getUpdateMovies())
            }
        }
    }

    @Test
    fun fullImportKeepsUnavailableTitlesReferencedByUserData() = runBlocking(Dispatchers.IO) {
        withRepository { database, repository ->
            val dao = database.movieDao()
            for (id in 1L..4L) dao.insert(MovieEntity(dbId = id, title = "Old $id", pageUrl = "/films/$id"))
            database.historyDao().insert(HistoryEntity(movieDbId = 1, position = 12345))
            database.favoritesDao().insert(FavoriteEntity(movieDbId = 2))
            database.playbackProgressDao().insert(PlaybackProgressEntity(
                movieDbId = 3, playableId = "film:3", positionMs = 23456,
            ))
            var completed = false
            repository.updateMovies(payload(), hasLastYearUpdate = false, forceAll = true) {
                if (it == 100) completed = true
            }
            assertTrue(completed)
            assertNotNull(dao.getMovie(1L))
            assertNotNull(dao.getMovie(2L))
            assertNotNull(dao.getMovie(3L))
            assertNull(dao.getMovie("/films/4"))
            assertEquals(1003, dao.getCount())
            assertEquals(23456L, database.playbackProgressDao().findByPlayable("default", "film:3")?.positionMs)
        }
    }

    @Test
    fun suspiciousFullPayloadDoesNotModifyExistingCatalog() = runBlocking(Dispatchers.IO) {
        withRepository { database, repository ->
            val dao = database.movieDao()
            dao.insert(MovieEntity(dbId = 7, title = "Original", pageUrl = "/films/7"))
            val before = dao.getUpdateMovies()
            try {
                repository.updateMovies(listOf(Movie(title = "Partial", pageUrl = "/films/8")), false, true) {}
                fail("Expected unsafe payload rejection")
            } catch (_: IllegalStateException) {
                assertEquals(before, dao.getUpdateMovies())
            }
        }
    }
}
