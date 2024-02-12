package com.arny.mobilecinema.presentation.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.data.player.VideoCache
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.getConnectionType
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
import com.google.android.exoplayer2.source.dash.manifest.DashManifest
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheSpan
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
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

    @get:Synchronized
    @set:Synchronized
    private var cacheFactory: CacheDataSource.Factory? = null
    private val handler = Handler(Looper.getMainLooper())

    @get:Synchronized
    @set:Synchronized
    private var downloadManager: DownloadManager? = null
    private var onUpdate: ((Float, Long, Long, Long, Int, Int) -> Unit?)? = null
    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            onUpdate?.invoke(
                download.percentDownloaded,
                download.bytesDownloaded,
                download.startTimeMs,
                download.updateTimeMs,
                download.state,
                downloadManager.currentDownloads.size
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
    ) -> Unit
    ) {
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
            C.CONTENT_TYPE_DASH -> getDashMediaSourceFactory(factory, mediaItem)
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
        try {
            downloadManager?.removeDownload(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val cache = VideoCache.getInstance(context).getDownloadCache()
        val segmentsData = try {
            getSegmentsData(url, dataSourceFactory())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        val keys = cache.keys
//        Timber.d("clearDownloaded ${keys.size}, ${segmentsData.segments.size}")
        try {
            if (segmentsData != null) {
                val cachedKeys = getCachedHlsKeys(segmentsData, keys.toList())
                cachedKeys.forEach { cache.removeResource(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    fun skipDownload(url: String) {
        handler.removeCallbacksAndMessages(null)
        ensureDownloadManagerInitialized(context)
        downloadManager?.currentDownloads?.forEach {
            if (it.request.id == url) {
                downloadManager?.removeDownload(it.request.id)
            }
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

    suspend fun getCurrentDownloadData(url: String): DownloadManagerData {
        return withContext(dispatcher) {
            ensureDownloadManagerInitialized(context)
//            Timber.d("getCurrentDownloadData init downloadManager")
            while (downloadManager?.isInitialized == false) {
//                Timber.d("wait downloadManager")
                delay(250)
            }
            var initialized = downloadManager?.isInitialized == true
            var isEquals = false
            var title = ""
            var downloadsEmpty = false
            var downloadPercent = 0.0f
            var downloadBytes = 0L
            val isMp4 = isMp4(url)
//            Timber.d("getCurrentDownloadData isMp4:$isMp4")
            val factory = dataSourceFactory()
            val cache = getCache()
            val cacheKeys = cache.keys.toList()
            val segmentsData: SegmentsData? =
                if (!isMp4) {
                    try {
                        getSegmentsData(url, factory)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } else {
                    null
                }
            val cachedKeysList = segmentsData?.let { getCachedHlsKeys(it, cacheKeys) }.orEmpty()
            val key = getCacheKey(url)
            val spans = if (isMp4) {
                cache.getCachedSpans(key)
            } else {
                segmentsData?.let { getCachedSpans(it) }.orEmpty()
            }
            val segments = segmentsData?.segments.orEmpty()
            downloadManager?.let { dManager ->
                initialized = dManager.isInitialized == true
                val downloads = dManager.currentDownloads
                downloadsEmpty = downloads.isEmpty()
                if (!downloadsEmpty) {
                    val download = downloads.find { it.request.id == url }
                    title = download?.request?.data?.toString(Charsets.UTF_8).orEmpty()
                }
                downloadPercent = if (isMp4) {
                    getDownloadedPercent(spans.size, segments.size)
                } else {
                    getDownloadedPercent(cachedKeysList.size, segments.size)
                }
                downloadBytes = if (isMp4) {
                    spans.sumOf { it.length }
                } else {
                    getDownloadedSize(cache, cachedKeysList)
                }
                isEquals = initialized && downloads.find { it.request.id == url } != null
//                Timber.d("downloadPercent:$downloadPercent, downloadBytes:$downloadBytes")
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

    private fun isMp4(url: String) = url.endsWith(".mp4")

    private fun getCachedSpans(segmentsData: SegmentsData): List<CacheSpan> =
        segmentsData.segments.map { s ->
            "${segmentsData.otherBaseUrl.substringBefore("/index")}/$s"
        }.flatMap { s ->
            cacheFactory?.cache?.getCachedSpans(s)?.filter { it.isCached }.orEmpty()
        }

    private fun getDownloadedPercent(
        cachedSize: Int,
        totalSize: Int
    ): Float {
        var percent = 0.0f
        if (totalSize != 0) {
            percent = ((cachedSize.toFloat() / totalSize.toFloat()) * 100)
            if (percent > 100.0) {
                percent = 100.0f
            }
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

    private fun getCachedHlsKeys(
        segmentsData: SegmentsData,
        cacheKeys: List<String>
    ): List<String> {
        val movieSegments = segmentsData.segments
        val hlsDomainName = getDomainName(segmentsData.hlsBaseUrl)
        val otherDomainName = getDomainName(segmentsData.otherBaseUrl)
        val list = if (movieSegments.isNotEmpty()) {
            if (!movieSegments.all { it.startsWith("http") }) {
                val filterByIndex = cacheKeys.filter {
                    it.indexOf("/index") == -1 && !it.endsWith("master.m3u8")
                }
                var filter = filterByIndex.filter { s ->
                    getDomainName(s) in otherDomainName  && s.indexOf("seg-") != -1
                }
                if (filter.isEmpty()) {
                    filter = filterByIndex.filter { s ->
                        getDomainName(s) in hlsDomainName && s.indexOf("seg-") != -1
                    }
                }
                if (movieSegments.all { it.startsWith("seg") }) {
                    val filterSegments = hashMapOf<String, Int>()
                    filter
                        .asSequence()
                        .map { "seg-${it.substringAfter("/seg-")}" }
                        .forEachIndexed { index, s ->
                            filterSegments[s] = index
                        }
                    val indexes = filterSegments
                        .filter { it.key in movieSegments }
                        .map { it.value }
                    filter.filterIndexed { index, _ -> index in indexes }
                } else {
                    filter.asSequence()
                        .filter {
                            movieSegments.any { s ->
                                it.contains(s, true)
                            }
                        }
                        .toList()
                }
            } else {
                cacheKeys.filter { it in segmentsData.segments }
            }
        } else {
            emptyList()
        }
        return list
    }

    private suspend fun getSegmentsData(
        url: String,
        cacheFactory: CacheDataSource.Factory
    ): SegmentsData = suspendCoroutine { continuation ->
        if (getConnectionType(context) != ConnectionType.NONE) {
            val mediaItem = getMediaItem(url)
            DownloadHelper.forMediaItem(
                /* context = */ context,
                /* mediaItem = */ mediaItem,
                /* renderersFactory = */ null,
                /* dataSourceFactory = */ cacheFactory
            ).prepare(
                object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        continuation.resumeWith(
                            Result.success(
                                when (val manifest = helper.manifest) {
                                    is HlsManifest -> getSegmentData(manifest)
                                    //                                    is DashManifest -> getSegmentData(manifest)
                                    else -> SegmentsData()
                                }
                            )
                        )
                    }

                    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                        Timber.e("getSegmentsData error ${e.message}")
                        e.printStackTrace()
                        continuation.resumeWithException(e)
                    }
                })
        } else {
            continuation.resumeWithException(Exception("no_internet_connection"))
        }
        }

    private fun getCacheKey(url: String): String =
        dataSourceFactory().cacheKeyFactory.buildCacheKey(DataSpec(Uri.parse(url)))

    private fun getSegmentData(manifest: HlsManifest): SegmentsData {
        return SegmentsData(
            hlsBaseUrl = manifest.multivariantPlaylist.baseUri.substringBefore("/master.m3u8"),
            otherBaseUrl = manifest.mediaPlaylist.baseUri,
            segments = manifest.mediaPlaylist.segments.map { it.url }
        )
    }

    private fun getSegmentData(manifest: DashManifest): SegmentsData {
        for (i in 0 until manifest.periodCount) {
            val period = manifest.getPeriod(i)
            for (j in 0 until period.adaptationSets.size) {
                val adaptationSet = period.adaptationSets[j]
                for (k in 0 until adaptationSet.representations.size) {
//                    println(adaptationSet.representations[k])
                }
            }
        }
        return SegmentsData(
            hlsBaseUrl = "",
            otherBaseUrl = "",
            segments = emptyList()
        )
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
    ): ProgressiveMediaSource = ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory())
        .createMediaSource(item)

    private fun getHlsMedialSource(
        factory: DataSource.Factory,
        item: MediaItem
    ): HlsMediaSource = HlsMediaSource.Factory(factory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(item)

    private fun getDashMediaSourceFactory(
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

    private fun getCache(): Cache = VideoCache.getInstance(context).getDownloadCache()

    private fun initCacheFactory() {
        val cache = getCache()
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