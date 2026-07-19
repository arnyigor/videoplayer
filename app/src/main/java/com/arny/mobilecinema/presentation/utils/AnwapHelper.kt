package com.arny.mobilecinema.presentation.utils

fun String.getWithDomain(baseUrl: String): String {
    val url = trim()
    if (url.startsWith("http", ignoreCase = true)) return url

    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    val normalizedPath = url.trimStart('/')
    return if (normalizedBaseUrl.isNotBlank() && normalizedPath.isNotBlank()) {
        "$normalizedBaseUrl/$normalizedPath"
    } else {
        ""
    }
}
