package com.arny.mobilecinema.presentation.player

/**
 * Short, user-facing description of a stream: host plus container.
 * Shared by the mobile details spinner and the TV source list so both show the same thing.
 */
internal fun shortSourceLabel(url: String): String {
    val withoutQuery = url.substringBefore("?")
    val host = withoutQuery.substringAfter("://", "").substringBefore("/")
    // No invented fallback text: a blank URL must stay blank instead of showing a fake container.
    val extension = withoutQuery.substringAfterLast(".", "").uppercase()
    return listOf(host, extension).filter { it.isNotBlank() }.joinToString(" • ")
}

/** "HD • o11.anwap.be • M3U8" — quality plus the shared host/container label. */
internal fun sourceLabel(quality: String, url: String): String = "$quality • ${shortSourceLabel(url)}"
