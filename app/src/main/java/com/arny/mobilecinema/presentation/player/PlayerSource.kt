package com.arny.mobilecinema.presentation.player

import android.content.Context
import android.net.Uri
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
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
import javax.inject.Inject

class PlayerSource @Inject constructor(
    private val context: Context
) {
    private val youTubeVideoRetriever = YouTubeVideoInfoRetriever()
    suspend fun getSource(url: String?): MediaSource? = url?.let {
        buildMediaSource1(url)
    }

    private fun getItem(url: String?): MediaItem =
        MediaItem.Builder().apply { setUri(Uri.parse(url)) }.build()

    private suspend fun buildMediaSource1(url: String): MediaSource {
        val uri = Uri.parse(url)
        val factory = dataSourceFactory()
        return when (val type: @C.ContentType Int = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> getDashMediaSource(factory, url)
            C.CONTENT_TYPE_HLS -> getHlsMedialSource(factory, url)
            C.CONTENT_TYPE_OTHER -> {
                when {
                    uri.host?.contains("youtube") == true ->
                        getYoutubeSource(url, factory)

                    uri.lastPathSegment.orEmpty().substringAfterLast('.') == "mp4" ->
                        getMp4MediaSource(factory, url)

                    else -> DashMediaSource.Factory(factory).createMediaSource(getItem(url))
                }
            }

            else -> error("Unsupported type: $type from url:$url")
        }
    }

    private fun getMp4MediaSource(
        factory: DataSource.Factory,
        url: String?
    ) = ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory())
        .createMediaSource(getItem(url))

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
        val url = data.streamingData?.formats?.find { it?.itag == 22 }?.url
        return when {
            !url.isNullOrBlank() -> getMp4MediaSource(factory, url)
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