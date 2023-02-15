package com.arny.mobilecinema.data.player

import android.content.Context
import com.arny.mobilecinema.data.utils.SingletonHolder
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

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