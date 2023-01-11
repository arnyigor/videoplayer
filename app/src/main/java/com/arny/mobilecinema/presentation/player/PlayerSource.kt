package com.arny.mobilecinema.presentation.player

import android.net.Uri
import com.arny.mobilecinema.data.repository.AppConstants
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashChunkSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import javax.inject.Inject

class PlayerSource @Inject constructor() {
    fun getSource(url: String?): MediaSource? = when {
        /* !url.isNullOrBlank() && url.endsWith(".m3u8") -> {
             hlsMediaSource(url)
         }

         !url.isNullOrBlank() && url.endsWith(".mpd") -> {
             dashMediaSource(url)
         }*/

        else -> buildMediaSource1(Uri.parse(url))
    }

    private fun buildMediaSource1(uri: Uri): MediaSource? {
        val item = MediaItem.Builder().apply {
            setUri(uri)
        }.build()
        return when (val type: @C.ContentType Int = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(dataSourceFactory())
                .createMediaSource(item)

            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory())
                .createMediaSource(item)

            C.CONTENT_TYPE_SS -> SsMediaSource.Factory(dataSourceFactory()).createMediaSource(item)
            C.CONTENT_TYPE_OTHER -> throw IllegalStateException("Unsupported type: $type")
            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }

    private fun dataSourceFactory(): DataSource.Factory {
//        val userAgent = Util.getUserAgent(context, context.getString(R.string.app_name))
//        val defaultDataSourceFactory = DefaultDataSourceFactory(
//            context,
//            userAgent
//        )
        val factory = DefaultHttpDataSource.Factory()
        factory.setUserAgent(AppConstants.USER_AGENT)
        return factory
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        val factory = DefaultHttpDataSource.Factory()
        factory.setUserAgent(AppConstants.USER_AGENT)
        val manifestDataSourceFactory: DataSource.Factory = factory
        val dashChunkSourceFactory: DashChunkSource.Factory =
            DefaultDashChunkSource.Factory(factory)
        val item = MediaItem.Builder().apply {
            setUri(uri)
        }.build()
        return DashMediaSource.Factory(
            dashChunkSourceFactory,
            manifestDataSourceFactory
        ).createMediaSource(item)
    }

    fun ff() {
        ConcatenatingMediaSource()
    }

    private fun hlsMediaSource(url: String): HlsMediaSource {
        val item = MediaItem.Builder().apply {
            setUri(url)
        }.build()
        val factory = DefaultHttpDataSource.Factory()
        factory.setUserAgent(AppConstants.USER_AGENT)
        return HlsMediaSource.Factory(factory)
            .createMediaSource(item)
    }

    private fun dashMediaSource(url: String): DashMediaSource {
        val item = MediaItem.Builder().apply {
            setUri(url)
        }.build()
        val factory = DefaultHttpDataSource.Factory()
        factory.setUserAgent(AppConstants.USER_AGENT)
        return DashMediaSource.Factory(factory).createMediaSource(item)
    }
}