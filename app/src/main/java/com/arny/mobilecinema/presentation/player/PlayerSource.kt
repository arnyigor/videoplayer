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
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
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
import java.lang.Exception
import java.util.concurrent.Executors
import javax.inject.Inject

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
                Executors.newSingleThreadExecutor()
            )
        }
    }

    fun cacheVideo(
        videoUrl: String,
        progressListener: (percent: Float, state: Int) -> Unit
    ) {
        ensureDownloadManagerInitialized(context)
        val builder = DownloadRequest.Builder(videoUrl, Uri.parse(videoUrl)).build()
        downloadManager?.addDownload(builder)
        downloadManager?.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                println("download:${download.getState()}")
                progressListener(download.percentDownloaded, download.state)
            }
        })
        downloadManager?.resumeDownloads()
        updateProgress(progressListener)
    }

    private fun updateProgress(progressListener: (percent: Float, state: Int) -> Unit) {
        handler.postDelayed({
            val currentDownloads = downloadManager?.currentDownloads
            if (currentDownloads?.isNotEmpty() == true) {
                val download = currentDownloads.first()
                progressListener(download.percentDownloaded, download.state)
            }
            updateProgress(progressListener)
        }, 1000)
    }

    suspend fun isDownloaded(url: String): Boolean = withContext(Dispatchers.IO) {
        ensureDownloadManagerInitialized(context)
        var download: Download? = null
        val mediaItem = getMediaItem(url)
        val uri = mediaItem.localConfiguration?.uri
        if (uri != null) {
            val downloads: HashMap<Uri, Download> = hashMapOf()
            downloadManager?.downloadIndex?.getDownloads()?.use { loadedDownloads ->
                while (loadedDownloads.moveToNext()) {
                    val value = loadedDownloads.download
                    val key = value.request.uri
                    downloads[key] = value
                }
            }
            download = downloads[uri]
        }
        val state = download.getState()
        println("state:$state,downloaded:${download?.percentDownloaded}")
        download != null && download.state != Download.STATE_FAILED
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
        VideoCache.getInstance(context).getDownloadCache().removeResource(url)
    }

    /**
     * Отмена загрузки, удаление всего что загрузили
     * Просто остановить загрузку нельзя
     */
    fun cancelDownload(url: String) {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
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