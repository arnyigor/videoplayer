package com.arny.mobilecinema.presentation.player

import android.content.Context
import android.net.Uri
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
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
import io.ktor.client.HttpClient
import javax.inject.Inject

class PlayerSource @Inject constructor(
    httpClient: HttpClient,
    private val context: Context
) {
    private companion object {
        const val YOUTUBE_HOST = "youtube"
        const val YOUTUBE_MAX_QUALITY_TAG = 22
    }

    private val youTubeVideoRetriever = YouTubeVideoInfoRetriever(httpClient)
    suspend fun getSource(url: String?): MediaSource? = url?.let {
        buildMediaSource1(url)
    }

    private fun getItem(url: String?, title: String? = null): MediaItem {
        return MediaItem.Builder().apply {
            if (title != null) {
                val builder = MediaMetadata.Builder()
                builder.setTitle(title)
                setMediaMetadata(builder.build())
            }
            setUri(Uri.parse(url))
        }
            .build()
    }

    private suspend fun buildMediaSource1(url: String): MediaSource {
        val uri = Uri.parse(url)
        val factory = dataSourceFactory()
        return when (val type: @C.ContentType Int = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> getDashMediaSource(factory, url)
            C.CONTENT_TYPE_HLS -> getHlsMedialSource(factory, url)
            C.CONTENT_TYPE_OTHER -> {
                when {
                    uri.host?.contains(YOUTUBE_HOST) == true ->
                        getYoutubeSource(url, factory)

                    uri.lastPathSegment.orEmpty().substringAfterLast('.') == "mp4" ->
                        getMp4MediaSource(factory, url)

                    else -> getMp4MediaSource(factory, url)
                }
            }
            else -> error("Unsupported type: $type from url:$url")
        }
    }

    private fun getMp4MediaSource(
        factory: DataSource.Factory,
        url: String?,
        title: String? = null
    ) = ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory())
        .createMediaSource(getItem(url, title))

    private fun getHlsMedialSource(
        factory: DataSource.Factory,
        url: String?
    ) = HlsMediaSource.Factory(factory).createMediaSource(getItem(url))

    private fun getDashMediaSource(
        factory: DataSource.Factory,
        url: String?
    ) = DashMediaSource.Factory(
        /* chunkSourceFactory = */ DefaultDashChunkSource.Factory(factory),
        /* manifestDataSourceFactory = */ factory
    ).createMediaSource(getItem(url))

    private suspend fun getYoutubeSource(
        link: String,
        factory: DataSource.Factory
    ): MediaSource {
        val result = "v=(.*?)(&|$)".toRegex().find(link)?.groupValues?.getOrNull(1).toString()
        val data = youTubeVideoRetriever.retrieve(result)
        val title = data.videoDetails?.title
        val format = data.streamingData?.formats?.find { it?.itag == YOUTUBE_MAX_QUALITY_TAG }
        val url = format?.url
        return when {
            !url.isNullOrBlank() -> getMp4MediaSource(factory, url, title)
            else -> error("Media source from Youtube link $link not found")
        }
    }

    private fun dataSourceFactory(): DataSource.Factory {
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