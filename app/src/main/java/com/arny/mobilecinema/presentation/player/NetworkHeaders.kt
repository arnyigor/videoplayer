package com.arny.mobilecinema.presentation.player

import java.net.URI
import java.util.Locale

/**
 * Some CDNs only serve video when the request carries a site-specific Referer.
 * Such URLs work in the built-in player (which sets the header) but cannot be handed
 * to an external app: ACTION_VIEW has no portable way to pass request headers.
 */
internal fun requiredVideoReferer(url: String): String? = when {
    url.contains("interkh.com", ignoreCase = true) -> "https://api.ortified.ws/"
    url.contains("ortified.ws", ignoreCase = true) -> "https://mm.anwap.media/"
    else -> null
}

/** MIME type for ACTION_VIEW, or null when the URL may not be opened outside the app. */
internal fun externalVideoMimeType(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase(Locale.ROOT) !in listOf("http", "https")) return null
    if (uri.host.isNullOrBlank()) return null
    if (requiredVideoReferer(url) != null) return null

    return when (uri.path.orEmpty().substringAfterLast('.').lowercase(Locale.ROOT)) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "video/*"
    }
}

/**
 * MIME-типы для выбора внешнего приложения, от точного к общему.
 * Многие плееры (VLC, MX) не объявляют «application/x-mpegURL», но принимают общий
 * видео-тип, поэтому только первый вариант давал ложное «нет приложения» на HLS.
 */
internal fun externalMimeCandidates(url: String): List<String> {
    val exact = externalVideoMimeType(url) ?: return emptyList()
    return if (exact == "video/*") listOf(exact) else listOf(exact, "video/*")
}
