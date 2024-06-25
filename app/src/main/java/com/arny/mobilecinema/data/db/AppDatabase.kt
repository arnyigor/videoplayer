package com.arny.mobilecinema.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.models.MovieEntity

@Database(
    entities = [MovieEntity::class, HistoryEntity::class],
    version = 2,
    autoMigrations =  [
        AutoMigration (
            from = 1,
            to = 2
        ),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DBNAME = "Movies"
    }
}