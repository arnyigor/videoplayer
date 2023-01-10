package com.arny.mobilecinema.presentation.player

import com.arny.mobilecinema.data.repository.AppConstants
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import javax.inject.Inject

class PlayerSource @Inject constructor() {
    fun getSource(url: String?): MediaSource? = when {
        !url.isNullOrBlank() && url.endsWith(".m3u8") -> {
            hlsMediaSource(url)
        }

        !url.isNullOrBlank() && url.endsWith(".mpd") -> {
            dashMediaSource(url)
        }

        else -> null
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