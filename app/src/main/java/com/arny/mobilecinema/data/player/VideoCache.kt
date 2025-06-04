package com.arny.mobilecinema.data.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.arny.mobilecinema.data.utils.SingletonHolder
import java.io.File

@UnstableApi
class VideoCache private constructor(private val context: Context) {
    private var downloadCache: SimpleCache? = null
    private val cacheTitle = "exoCache"

    companion object : SingletonHolder<VideoCache, Context>(::VideoCache)

    @Synchronized
    fun getDownloadCache(): SimpleCache {
        if (downloadCache == null) {
            downloadCache = SimpleCache(
                File(context.cacheDir, cacheTitle),
                NoOpCacheEvictor(),
                StandaloneDatabaseProvider(context)
            )
        }
        return downloadCache!!
    }
}