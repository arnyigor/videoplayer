package com.arny.mobilecinema.presentation.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.data.player.VideoCache
import com.arny.mobilecinema.data.repository.AppConstants
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadHelper
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PlayerSource @Inject constructor(
    private val context: Context,
    private val retriever: YouTubeVideoInfoRetriever
) {
    private companion object {
        const val YOUTUBE_HOST = "youtube"
        const val YOUTUBE_MAX_QUALITY_TAG = 22
    }

    private val handler = Handler(Looper.getMainLooper())
    private var downloadManager: DownloadManager? = null

    suspend fun getSource(
        url: String?,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null
    ): MediaSource? = url?.let {
        val uri = Uri.parse(url)
        val factory = dataSourceFactory()
        val mediaItem = getMediaItem(url, title, season, episode)
        when (val type: @C.ContentType Int = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> getDashMediaSource(factory, mediaItem)
            C.CONTENT_TYPE_HLS -> getHlsMedialSource(factory, mediaItem)
            C.CONTENT_TYPE_OTHER -> {
                when {
                    uri.host?.contains(YOUTUBE_HOST) == true -> getYoutubeSource(url, factory)
                    uri.lastPathSegment.orEmpty().substringAfterLast('.') == "mp4" ->
                        getMp4MediaSource(factory, mediaItem)
                    else -> getMp4MediaSource(factory, mediaItem)
                }
            }
            else -> error("Unsupported type: $type from url:$url")
        }
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        if (downloadManager == null) {
            downloadManager = DownloadManager(
                /* context = */ context,
                /* databaseProvider = */
                StandaloneDatabaseProvider(context),
                /* cache = */
                VideoCache.getInstance(context).getDownloadCache(),
                /* upstreamFactory = */
                DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()),
                /* executor = */
                Executors.newScheduledThreadPool(3)
            )
        }
    }

    fun cacheVideo(
        videoUrl: String,
        progressListener: (percent: Float, bytes: Long, startTime: Long, updateTime: Long, state: Int) -> Unit
    ) {
        ensureDownloadManagerInitialized(context)
        val builder = DownloadRequest.Builder(videoUrl, Uri.parse(videoUrl)).build()
        downloadManager?.addDownload(builder)
        val start = System.currentTimeMillis()
        downloadManager?.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                progressListener(
                    download.percentDownloaded,
                    download.bytesDownloaded,
                    download.startTimeMs,
                    download.updateTimeMs,
                    download.state
                )
            }
        })
        downloadManager?.resumeDownloads()
        updateProgress(progressListener, start)
    }

    private fun updateProgress(
        progressListener: (percent: Float, bytes: Long, startTime: Long, updateTime: Long, state: Int) -> Unit,
        start: Long
    ) {
        handler.postDelayed({
            val currentDownloads = downloadManager?.currentDownloads
            if (currentDownloads?.isNotEmpty() == true) {
                val download = currentDownloads.first()
                progressListener(
                    download.percentDownloaded,
                    download.bytesDownloaded,
                    download.startTimeMs,
                    download.updateTimeMs,
                    download.state
                )
            }
            updateProgress(progressListener, start)
        }, 1000)
    }

    suspend fun isDownloaded(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            val factory = dataSourceFactory()
            val cache = factory.cache
            val keys = cache?.keys.orEmpty().toList()
            val segments = getSegments(url)
            val allCached: Boolean = if (segments.isNotEmpty()) {
                segments.all { it in keys }
            } else {
                keys.contains(url)
            }
            allCached
        }
    }

    private suspend fun getSegments(url: String): List<String> {
        return suspendCoroutine { continuation ->
            val mediaItem = getMediaItem(url)
            val helper = DownloadHelper.forMediaItem(context, mediaItem, null, dataSourceFactory())
            helper.prepare(
                object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        val segments = when (val manifest = helper.manifest) {
                            is HlsManifest -> {
                                manifest.mediaPlaylist.segments.map { it.url }
                            }
                            else -> emptyList()
                        }
                        continuation.resumeWith(Result.success(segments))
                    }

                    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                        e.printStackTrace()
                        continuation.resumeWithException(e)
                    }
                })
        }
    }

    private fun Download?.getState(): String {
        return if (this != null) {
            when (this.state) {
                Download.STATE_QUEUED -> "STATE_QUEUED"
                Download.STATE_STOPPED -> "STATE_STOPPED"
                Download.STATE_DOWNLOADING -> "STATE_DOWNLOADING"
                Download.STATE_COMPLETED -> "STATE_COMPLETED"
                Download.STATE_FAILED -> "STATE_FAILED"
                Download.STATE_REMOVING -> "STATE_REMOVING"
                Download.STATE_RESTARTING -> "STATE_RESTARTING"
                else -> "UNKNOWN"
            }
        } else {
            "null"
        }
    }

    suspend fun clearDownloaded(url: String) = withContext(Dispatchers.IO) {
        ensureDownloadManagerInitialized(context)
        downloadManager?.removeDownload(url)
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val segments = getSegments(url)
        segments.forEach { cache.removeResource(it) }
        cache.removeResource(url)
    }

    /**
     * Отмена загрузки, удаление всего что загрузили
     * Просто остановить загрузку нельзя
     */
    fun cancelDownload(url: String) {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
        downloadManager?.setStopReason(url, Download.STOP_REASON_NONE)
        downloadManager?.removeDownload(url)
    }

    private fun getMediaItem(
        url: String?,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null
    ): MediaItem {
        return MediaItem.Builder().apply {
            val builder = MediaMetadata.Builder()
            if (!title.isNullOrBlank()) {
                builder.setTitle(title)
            }
            if (season != null && episode != null) {
                builder.setExtras(
                    bundleOf(
                        AppConstants.Player.SEASON to season,
                        AppConstants.Player.EPISODE to episode
                    )
                )
            }
            setMediaMetadata(builder.build())
            setUri(Uri.parse(url))
        }
            .build()
    }

    private fun getMp4MediaSource(
        factory: DataSource.Factory,
        item: MediaItem
    ) = ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory())
        .createMediaSource(item)

    private fun getHlsMedialSource(
        factory: DataSource.Factory,
        item: MediaItem
    ) = HlsMediaSource.Factory(factory).createMediaSource(item)

    private fun getDashMediaSource(
        factory: DataSource.Factory,
        item: MediaItem
    ) = DashMediaSource.Factory(
        /* chunkSourceFactory = */ DefaultDashChunkSource.Factory(factory),
        /* manifestDataSourceFactory = */ factory
    ).createMediaSource(item)

    private suspend fun getYoutubeSource(
        link: String,
        factory: DataSource.Factory
    ): MediaSource {
        val result = "v=(.*?)(&|$)".toRegex().find(link)?.groupValues?.getOrNull(1).toString()
        val data = retriever.retrieve(result)
        val title = data.videoDetails?.title
        val format = data.streamingData?.formats?.find { it?.itag == YOUTUBE_MAX_QUALITY_TAG }
        val url = format?.url
        return when {
            !url.isNullOrBlank() -> getMp4MediaSource(factory, getMediaItem(url, title))
            else -> error("Media source from Youtube link $link not found")
        }
    }

    private fun dataSourceFactory(): CacheDataSource.Factory {
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val cacheSink = CacheDataSink.Factory().setCache(cache)
        val upstreamFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(cacheSink)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}