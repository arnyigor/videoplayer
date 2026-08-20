package com.arny.mobilecinema.data.player

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.StatFs
import com.arny.mobilecinema.data.utils.SingletonHolder
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

class VideoCache private constructor(private val context: Context) {
    private var downloadCache: SimpleCache? = null
    private var streamCache: SimpleCache? = null

    private val downloadCacheDir = File(context.cacheDir, "exoDownloadCache")
    private val streamCacheDir = File(context.cacheDir, "exoStreamCache")
    private val legacyCacheDir = File(context.cacheDir, "exoCache")

    companion object : SingletonHolder<VideoCache, Context>(::VideoCache) {
        private const val STREAM_CACHE_MIN_BYTES = 200L * 1024 * 1024 // 200 MB
        private const val STREAM_CACHE_MAX_BYTES = 1536L * 1024 * 1024 // 1.5 GB
    }

    init {
        // Удаляем устаревший неограниченный кэш (NoOpCacheEvictor), который мог бессистемно
        // заполнить внутреннюю память на TV и привести к падению приложения "через время".
        clearLegacyCache()
    }

    @Synchronized
    fun getDownloadCache(): SimpleCache {
        if (downloadCache == null) {
            downloadCache = SimpleCache(
                downloadCacheDir,
                NoOpCacheEvictor(),
                StandaloneDatabaseProvider(context)
            )
        }
        return downloadCache!!
    }

    @Synchronized
    fun getStreamCache(): SimpleCache {
        if (streamCache == null) {
            streamCache = SimpleCache(
                streamCacheDir,
                LeastRecentlyUsedCacheEvictor(computeStreamCacheMaxBytes()),
                CacheDatabaseProvider(context, "exo_stream_cache_index")
            )
        }
        return streamCache!!
    }

    private fun computeStreamCacheMaxBytes(): Long {
        return try {
            val stat = StatFs(context.cacheDir.absolutePath)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            (available * 0.25).toLong().coerceIn(STREAM_CACHE_MIN_BYTES, STREAM_CACHE_MAX_BYTES)
        } catch (_: Exception) {
            STREAM_CACHE_MAX_BYTES
        }
    }

    @Synchronized
    private fun clearLegacyCache() {
        if (legacyCacheDir.exists() && legacyCacheDir.isDirectory) {
            legacyCacheDir.deleteRecursively()
        }
    }

    /**
     * Минимальная реализация [DatabaseProvider] с собственным именем БД, чтобы стрим-кэш
     * не делил индекс с кэшем офлайн-загрузок (у [StandaloneDatabaseProvider] в 2.19.1
     * нет конструктора с именем). Таблицы создаются [com.google.android.exoplayer2.upstream.cache.CachedContentIndex]
     * при первом обращении [SimpleCache].
     */
    private class CacheDatabaseProvider(
        context: Context,
        private val databaseName: String
    ) : DatabaseProvider {
        private val helper = object : SQLiteOpenHelper(
            context.applicationContext,
            databaseName,
            null,
            1
        ) {
            override fun onCreate(db: SQLiteDatabase) = Unit
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        override fun getReadableDatabase(): SQLiteDatabase = helper.readableDatabase
        override fun getWritableDatabase(): SQLiteDatabase = helper.writableDatabase
    }
}
