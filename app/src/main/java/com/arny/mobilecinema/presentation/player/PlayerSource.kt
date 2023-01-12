package com.arny.mobilecinema.presentation.player

import android.content.Context
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

class PlayerSource @Inject constructor(
    private val context: Context
) {
    fun getSource(url: String?): MediaSource? {
        return buildMediaSource1(Uri.parse(url))
    }

    private fun buildMediaSource1(uri: Uri): MediaSource? {
        val item = MediaItem.Builder().apply {
            setUri(uri)
        }.build()
        val factory = dataSourceFactory()
        return when (val type: @C.ContentType Int = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(
                /* chunkSourceFactory = */ DefaultDashChunkSource.Factory(factory),
                /* manifestDataSourceFactory = */ factory
            ).createMediaSource(item)

            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(factory).createMediaSource(item)
            C.CONTENT_TYPE_OTHER -> {
                if (uri.host?.contains("youtube") == true) {
                    return SsMediaSource.Factory(factory).createMediaSource(item)
                }
                return DashMediaSource.Factory(factory).createMediaSource(item)
            }

            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }

    private fun dataSourceFactory(): DataSource.Factory {
        val factory = DefaultHttpDataSource.Factory()
        factory.setUserAgent(AppConstants.USER_AGENT)
        return factory
    }
}