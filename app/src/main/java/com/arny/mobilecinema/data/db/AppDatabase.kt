package com.arny.mobilecinema.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.mobilecinema.data.db.daos.FavoritesDao
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.FavoriteEntity
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.models.MovieEntity

@Database(
    entities = [MovieEntity::class, HistoryEntity::class, FavoriteEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        const val DBNAME = "Movies"

        /**
         * Миграция 1 → 2
         * Добавляет уникальный индекс на поля title и pageUrl в таблице movies
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Создаем уникальный индекс
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_title_pageUrl` 
                    ON `movies` (`title`, `pageUrl`)
                    """.trimIndent()
                )
                // Добавляем колонку addedToHistory в movies
                db.execSQL(
                    "ALTER TABLE `movies` ADD COLUMN `addedToHistory` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Миграция 2 → 3
         * 1. Добавляет поле addedToHistory в таблицу movies
         * 2. Добавляет поле customData в таблицу movies
         * 3. Добавляет поле latestTime с значением по умолчанию в таблицу history
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем колонку customData в movies (nullable)
                db.execSQL(
                    "ALTER TABLE `movies` ADD COLUMN `customData` TEXT"
                )

                // Добавляем колонку latestTime в history с дефолтным значением
                db.execSQL(
                    "ALTER TABLE `history` ADD COLUMN `latest_time` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         *  Миграция 3 → 4
         * новая миграция – добавляем таблицу favorites -
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `dbId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `movie_dbid` INTEGER NOT NULL,
                        `latest_time` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                // уникальный индекс на movie_dbid
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_movie_dbid` ON `favorites` (`movie_dbid`)"
                )
                // индекс для сортировки по latest_time
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorites_latest_time` ON `favorites` (`latest_time`)"
                )
            }
        }
    }
}
