package com.arny.mobilecinema.presentation.utils

fun String.getWithDomain(baseUrl: String): String {
    var url = this
    if (!url.startsWith("http")) {
        url = if (baseUrl.endsWith("/")) {
            "$baseUrl$url"
        } else {
            "$baseUrl/$url"
        }
    }
    return url
}