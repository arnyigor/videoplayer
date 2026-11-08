package com.arny.mobilecinema.data.utils

import java.net.URI

/** Original first, at most one retry. Rebase only site images; an external HTTP CDN may retry its own HTTPS origin. */
fun posterUrlCandidates(path: String?, baseUrl: String): List<String> {
    val value = path?.trim()?.replace("&amp;", "&")?.replace(" ", "%20").orEmpty()
    if (value.isBlank()) return emptyList()
    // A poster obtained from a remote catalogue must not be interpreted as a local file/content URI.
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(value)?.groupValues?.get(1).orEmpty()
    if (scheme.isNotEmpty() && !scheme.equals("http", true) && !scheme.equals("https", true)) return emptyList()
    val original = value.getWithDomain(baseUrl)
    val uri = runCatching { URI(original) }.getOrNull() ?: return emptyList()
    val host = uri.host?.lowercase() ?: return emptyList()
    if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return emptyList()
    val current = getDomainName(baseUrl).ifBlank {
        getDomainName(com.arny.mobilecinema.data.repository.AppConstants.ENTRY_POINT_URL)
    }
    val currentHost = runCatching { URI(current).host?.lowercase() }.getOrNull()
    val siteOwned = host == currentHost || host == "anwap.media" || host == "anwap.love" ||
        host.endsWith(".anwap.media") || host.endsWith(".anwap.love")
    val alternative = when {
        current.isNotBlank() && siteOwned && current != getDomainName(original) ->
            current + uri.rawPath.orEmpty() + uri.rawQuery?.let { "?$it" }.orEmpty()
        uri.scheme.equals("http", true) && current.startsWith("https://", true) ->
            "https:" + original.substringAfter(':')
        else -> null
    }
    return listOfNotNull(original, alternative).distinct()
}
