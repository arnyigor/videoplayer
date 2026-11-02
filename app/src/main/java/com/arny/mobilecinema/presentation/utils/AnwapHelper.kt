package com.arny.mobilecinema.presentation.utils

import com.arny.mobilecinema.data.repository.AppConstants

/**
 * Абсолютный URL постера. Relative path без настроенного baseUrl раньше давал пустую
 * строку и карточки оставались без изображений на устройстве.
 */
fun String.getWithDomain(baseUrl: String): String {
    val url = trim()
    if (url.isEmpty()) return ""
    if (url.startsWith("http", ignoreCase = true)) return url
    if (url.startsWith("//")) return "https:$url"
    val base = baseUrl.trim().ifBlank { AppConstants.ENTRY_POINT_URL }.trimEnd('/')
    val path = url.trimStart('/')
    return if (path.isBlank()) "" else "$base/$path"
}
