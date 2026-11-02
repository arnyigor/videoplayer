package com.arny.mobilecinema.presentation.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import com.arny.mobilecinema.data.repository.AppConstants
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import okhttp3.OkHttpClient

/**
 * Media3-адаптер тех же правил выбора источника, что и [PlayerSource] (план v3 §4.4, S-011).
 *
 * Задача адаптера — позволить постепенно заменить engine, не меняя контракты экрана: сюда
 * передаётся тот же URL, здесь те же правила для DASH/HLS/MP4 и те же метаданные серии.
 * Пока используется только в проверочных сценариях: экран всё ещё на ExoPlayer2.
 *
 * @param httpClient небезопасный клиент (устаревшие сертификаты) — передаётся снаружи, чтобы
 * адаптер не дублировал SSL-настройки приложения.
 */
@OptIn(UnstableApi::class)
class PlayerSourceMedia3(
    private val httpClient: OkHttpClient,
    private val userAgent: String,
) {

    fun dataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)

    /**
     * Источник для наблюдения именно тех типов, что уже поддержаны плеером.
     * Неподдерживаемая схема — ошибка, а не молчаливый fallback.
     */
    fun source(
        url: String,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        mediaId: String? = null,
    ): MediaSource {
        require(url.isNotBlank()) { "blank media url" }
        val uri = Uri.parse(url)
        val mediaItem = mediaItem(url, title, season, episode, mediaId)
        val factory = dataSourceFactory()
        return when (Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
            C.CONTENT_TYPE_DASH ->
                // DASH намеренно не подключён в этом адаптере: приложение его пока не использует
                // на mobile-экране, а частичная поддержка маскировала бы регрессию.
                error("DASH is not migrated yet: $url")
            else -> ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory())
                .createMediaSource(mediaItem)
        }
    }

    private fun mediaItem(
        url: String,
        title: String?,
        season: Int?,
        episode: Int?,
        mediaId: String?,
    ): MediaItem = MediaItem.Builder()
        .setUri(url)
        .apply { if (!mediaId.isNullOrBlank()) setMediaId(mediaId) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .apply { if (!title.isNullOrBlank()) setTitle(title) }
                .apply {
                    if (season != null && episode != null) {
                        setExtras(
                            android.os.Bundle().apply {
                                putInt(AppConstants.Player.SEASON, season)
                                putInt(AppConstants.Player.EPISODE, episode)
                            }
                        )
                    }
                }
                .build()
        )
        .build()
}
