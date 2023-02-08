package com.arny.mobilecinema.presentation.utils

import com.arny.mobilecinema.BuildConfig

fun String.getWithDomain(): String {
    var url = this
    if (!url.startsWith("http")) {
        url = BuildConfig.base_link + url
    }
    return url
}