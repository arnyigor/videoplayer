package com.arny.mobilecinema.presentation.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.data.player.VideoCache
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.domain.models.DownloadManagerData
import com.arny.mobilecinema.domain.repository.UpdateRepository
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
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PlayerSource @Inject constructor(
    private val context: Context,
    private val updateRepository: UpdateRepository,
    private val retriever: YouTubeVideoInfoRetriever,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private companion object {
        const val YOUTUBE_HOST = "youtube"
        const val YOUTUBE_MAX_QUALITY_TAG = 22
    }

    private var cacheFactory: CacheDataSource.Factory? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downloadManager: DownloadManager? = null
    private var onUpdate: ((Float, Long, Long, Long, Int, Int) -> Unit?)? = null
    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val size = downloadManager.currentDownloads.size
            onUpdate?.invoke(
                download.percentDownloaded,
                download.bytesDownloaded,
                download.startTimeMs,
                download.updateTimeMs,
                download.state,
                size
            )
        }
    }

    fun setListener(progressListener: (
        percent: Float,
        bytes: Long,
        startTime: Long,
        updateTime: Long,
        state: Int,
        size: Int
    ) -> Unit) {
        onUpdate = progressListener
        downloadManager?.addListener(downloadListener)
    }

    fun removeListener() {
        onUpdate = null
        downloadManager?.removeListener(downloadListener)
    }

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

    fun cacheVideo(videoUrl: String, currentTitle: String) {
        ensureDownloadManagerInitialized(context)
        val build = DownloadRequest.Builder(videoUrl, Uri.parse(videoUrl))
            .setData(currentTitle.toByteArray(Charsets.UTF_8))
            .build()
        handler.postDelayed({
            if (downloadManager?.isInitialized == true) {
                downloadManager?.addDownload(build)
                downloadManager?.resumeDownloads()
                updateProgress()
            }
        }, 1000)
    }

    private fun updateProgress() {
        handler.postDelayed({
            updateCurrentDownloads()
            updateProgress()
        }, 1000)
    }

    private fun updateCurrentDownloads() {
        val currentDownloads = downloadManager?.currentDownloads
        if (currentDownloads?.isNotEmpty() == true) {
            val download = currentDownloads.first()
            onUpdate?.invoke(
                download.percentDownloaded,
                download.bytesDownloaded,
                download.startTimeMs,
                download.updateTimeMs,
                download.state,
                currentDownloads.size
            )
        }
    }

    suspend fun clearDownloaded(url: String) = withContext(dispatcher) {
        ensureDownloadManagerInitialized(context)
        downloadManager?.removeDownload(url)
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val segmentsData = getSegmentsData(url, dataSourceFactory())
        segmentsData.segments.forEach { cache.removeResource(it) }
        cache.removeResource(url)
    }

    suspend fun clearAllDownloaded() = withContext(dispatcher) {
        ensureDownloadManagerInitialized(context)
        downloadManager?.currentDownloads?.forEach {
            downloadManager?.removeDownload(it.request.id)
        }
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val keys = cache.keys
        keys.forEach { cache.removeResource(it) }
    }

    fun cancelDownload(url: String) {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
        downloadManager?.setStopReason(url, Download.STOP_REASON_NONE)
        downloadManager?.currentDownloads?.forEach {
            downloadManager?.removeDownload(it.request.id)
        }
    }

    fun pauseDownload() {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
        downloadManager?.pauseDownloads()
        handler.post { updateCurrentDownloads() }
    }

    fun resumeDownloads() {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
        downloadManager?.resumeDownloads()
        updateProgress()
    }

    suspend fun getCurrentDownloadData(cinemaUrl: String): DownloadManagerData {
        return withContext(dispatcher) {
            ensureDownloadManagerInitialized(context)
            var initialized = downloadManager?.isInitialized == true
            if (!initialized) {
                delay(1000) // wait init
            }
            var isEquals = false
            var title = ""
            var downloadsEmpty = false
            var downloadPercent = 0.0f
            var downloadBytes = 0L
            val factory = dataSourceFactory()
            val cache = factory.cache
            val segmentsData = getSegmentsData(cinemaUrl, factory)
            val list = cache?.let { getCachedKeys(it, cinemaUrl, segmentsData) }.orEmpty()
            downloadManager?.let { dManager ->
                initialized = dManager.isInitialized == true
                val downloads = dManager.currentDownloads
                downloadsEmpty = downloads.isEmpty()
                if (!downloadsEmpty) {
                    // TODO обработать множество загрузок
                    val download = downloads.find { it.request.id == cinemaUrl }
                    title = download?.request?.data?.toString(Charsets.UTF_8).orEmpty()
                    downloadPercent = getDownloadedPercent(segmentsData, list)
                    downloadBytes = getDownloadedSize(cache, list)
                } else {
                    downloadPercent = getDownloadedPercent(segmentsData, list)
                    downloadBytes = getDownloadedSize(cache, list)
                }
                isEquals = initialized && downloads.find { it.request.id == cinemaUrl } != null
            }
            DownloadManagerData(
                isInitValid = initialized,
                downloadsEmpty = downloadsEmpty,
                isEqualsLinks = isEquals,
                movieTitle = title,
                downloadPercent = downloadPercent,
                downloadBytes = downloadBytes
            )
        }
    }

    private fun getDownloadedPercent(
        segmentsData: SegmentsData,
        list: List<String>
    ): Float {
        val cached = list.size.toFloat()
        val total = segmentsData.segments.size.toFloat()
        var percent = ((cached / total) * 100)
        if (percent > 100.0) {
            percent = 100.0f
        }
        return percent
    }

    private fun getDownloadedSize(
        cache: Cache?,
        list: List<String>
    ): Long {
        var size = 0L
        cache?.let {
            size = list.flatMap { key ->
                cache.getCachedSpans(key).map { span -> span.length }
            }.sum()
        }
        return size
    }

    private fun getCachedKeys(
        cache: Cache,
        url: String,
        segmentsData: SegmentsData
    ): List<String> {
        val cacheKeys = cache.keys.toList()
        val segments = segmentsData.segments
        return if (segments.isNotEmpty()) {
            if (!segments.all { it.startsWith("http") }) {
                val urls = cacheKeys.filter { it in segmentsData.baseUrls }
                    .map { getDomainName(it) }
                    .distinct()
                val filter = cacheKeys.filter { urls.any { u -> it.contains(u, true) } }
                    .filter { !it.contains("/index") }
                    .filter { segments.any { s -> it.contains(s, true) } }
                filter
            } else {
                cacheKeys.filter { it in segmentsData.segments }
            }
        } else {
            if (cacheKeys.contains(element = url)) {
                cacheKeys.filter { it.contains(other = url) }
            } else {
                emptyList()
            }
        }
    }

    private suspend fun getSegmentsData(
        url: String,
        factory: CacheDataSource.Factory
    ): SegmentsData =
        suspendCoroutine { continuation ->
            val mediaItem = getMediaItem(url)
            DownloadHelper.forMediaItem(
                /* context = */ context,
                /* mediaItem = */ mediaItem,
                /* renderersFactory = */ null,
                /* dataSourceFactory = */ factory
            )
                .prepare(
                    object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper) {
                            val segments = when (val manifest = helper.manifest) {
                                is HlsManifest -> getSegmentData(manifest)
                                else -> SegmentsData()
                            }
                            continuation.resumeWith(Result.success(segments))
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            e.printStackTrace()
                            continuation.resumeWithException(e)
                        }
                    })
        }

    private fun getSegmentData(manifest: HlsManifest): SegmentsData {
        val playlist = manifest.mediaPlaylist
        val segments = playlist.segments.map { it.url }
        return SegmentsData(listOf(playlist.baseUri), segments)
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
            var urlFull = url
            val baseUrl = updateRepository.baseUrl
            if (baseUrl.isNotBlank() && !urlFull.isNullOrBlank() && !urlFull.startsWith("http")) {
                urlFull = "$baseUrl/$urlFull"
            }
            setUri(Uri.parse(urlFull))
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
    ) = HlsMediaSource.Factory(factory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(item)

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
            !url.isNullOrBlank() -> getMp4MediaSource(
                factory, getMediaItem(url, title)
            )
            else -> error("Media source from Youtube link $link not found")
        }
    }

    private fun dataSourceFactory(): CacheDataSource.Factory {
        if (cacheFactory == null) {
            initCacheFactory()
        }
        return cacheFactory!!
    }

    private fun initCacheFactory() {
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val cacheSink = CacheDataSink.Factory().setCache(cache)
        val upstreamFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
        cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(cacheSink)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}