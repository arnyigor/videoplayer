package com.arny.mobilecinema.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Historical exported schemas 3-7 -> current schema, using private test databases only. */
class DatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation, AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    private val migrations = arrayOf(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)

    private fun insertRow(db: SupportSQLiteDatabase, table: String, fields: Map<String, Any>) {
        val values = ContentValues()
        db.query("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.moveToNext()) {
                val name = columns.getString(columns.getColumnIndexOrThrow("name"))
                val type = columns.getString(columns.getColumnIndexOrThrow("type"))
                val required = columns.getInt(columns.getColumnIndexOrThrow("notnull")) != 0
                val value = fields[name] ?: if (!required) null else when (type) {
                    "INTEGER" -> 0L
                    "REAL" -> 0.0
                    else -> ""
                }
                when (value) {
                    is Int -> values.put(name, value)
                    is Long -> values.put(name, value)
                    is Double -> values.put(name, value)
                    is String -> values.put(name, value)
                }
            }
        }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun verifyMigration(from: Int) {
        DeviceGuard.requireApprovedDevice()
        val context = instrumentation.targetContext
        val name = "migration-$from-${System.nanoTime()}"
        try {
            helper.createDatabase(name, from).use { db ->
                insertRow(db, "movies", mapOf("dbId" to 7L, "title" to "Original film", "origTitle" to "Film original",
                    "pageUrl" to "/films/7", "type" to MovieType.CINEMA.value, "customData" to "user note"))
                insertRow(db, "movies", mapOf("dbId" to 8L, "title" to "Original series", "pageUrl" to "/serials/8",
                    "type" to MovieType.SERIAL.value))
                insertRow(db, "history", mapOf("dbId" to 1L, "movie_dbid" to 8L, "position" to 90000L,
                    "season" to 0, "episode" to 1, "latest_time" to 123456L))
                if (from >= 4) insertRow(db, "favorites", mapOf("dbId" to 1L, "movie_dbid" to 7L, "latest_time" to 654321L))
                if (from == 7) {
                    insertRow(db, "playback_progress", mapOf("dbId" to 1L, "movie_dbid" to 7L,
                        "season" to 0, "episode" to 0, "played_ms" to 12345L, "latest_time" to 99L))
                    insertRow(db, "playback_progress", mapOf("dbId" to 2L, "movie_dbid" to 8L,
                        "season" to 0, "episode" to 1, "played_ms" to 23456L, "latest_time" to 88L))
                }
            }
            helper.runMigrationsAndValidate(name, 8, true, *migrations).use { db ->
                db.query("SELECT position, season, episode, latest_time, last_playable_id, duration_ms, completed FROM history WHERE movie_dbid = 8").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(90000L, it.getLong(0))
                    assertEquals(0, it.getInt(1))
                    assertEquals(1, it.getInt(2))
                    assertEquals(123456L, it.getLong(3))
                    assertEquals("", it.getString(4))
                    assertEquals(0L, it.getLong(5))
                    assertEquals(0, it.getInt(6))
                }
                db.query("SELECT COUNT(*) FROM favorites").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(if (from >= 4) 1 else 0, it.getInt(0))
                }
                if (from >= 4) {
                    db.query("SELECT movie_dbid, latest_time FROM favorites").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(7L, it.getLong(0))
                        assertEquals(654321L, it.getLong(1))
                    }
                }
                if (from == 7) {
                    assertThrows(SQLiteConstraintException::class.java) {
                        insertRow(db, "playback_progress", mapOf("dbId" to 99L, "movie_dbid" to 8L,
                            "playable_id" to "legacy:8:0:1", "profile_id" to "default"))
                    }
                }
                assertThrows(SQLiteConstraintException::class.java) {
                    insertRow(db, "movies", mapOf("dbId" to 99L, "title" to "Original film", "pageUrl" to "/films/7"))
                }
            }
            // Opening the migrated file through the actual Room database validates its identity too.
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*migrations).build()
            try {
                val movie = room.movieDao().getMovie(7L)
                assertNotNull(movie)
                assertEquals("Film original", movie?.origTitle)
                assertEquals("user note", movie?.customData)
                assertEquals(0L, movie?.detailsFetchedAt)
                assertEquals(2, room.movieDao().getCount())
                if (from == 7) {
                    val film = room.playbackProgressDao().findByPlayable("default", "legacy:7:0:0")
                    val series = room.playbackProgressDao().findByPlayable("default", "legacy:8:0:1")
                    assertEquals(12345L, film?.playedMs)
                    assertEquals(0, film?.seasonNumber)
                    assertEquals(0, film?.episodeNumber)
                    assertEquals(23456L, series?.playedMs)
                    assertEquals(1, series?.seasonNumber)
                    assertEquals(2, series?.episodeNumber)
                    assertEquals(88L, series?.updatedAt)
                    assertEquals(0L, series?.positionMs)
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun migration3To8PreservesUserData() = verifyMigration(3)
    @Test fun migration4To8PreservesUserData() = verifyMigration(4)
    @Test fun migration5To8PreservesUserData() = verifyMigration(5)
    @Test fun migration6To8PreservesUserData() = verifyMigration(6)
    @Test fun migration7To8BackfillsProgressAndPreservesUserData() = verifyMigration(7)
}
