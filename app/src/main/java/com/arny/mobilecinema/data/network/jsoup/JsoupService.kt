package com.arny.mobilecinema.data.network.jsoup

import com.arny.mobilecinema.data.utils.getDomainName
import org.jsoup.helper.Validate
import org.jsoup.nodes.Document
import javax.inject.Inject

class JsoupService @Inject constructor() {
    companion object {
        fun getInstance() = JsoupService()
    }

    fun loadPage(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        logLevel: LogLevel = LogLevel.NONE,
        timeout: Int = 10000,
        resetCookie: Boolean = false
    ): Document = JsoupLoggerConnection.connect(url, logLevel, resetCookie).apply {
        userAgent(JsoupServiceHelper.UA)
        headers(requestHeaders)
        val cookie = JsoupServiceHelper.cookie
        if (resetCookie && cookie.isNotEmpty()) {
            cookies(cookie)
        }
        timeout(timeout)
        followRedirects(true)
        ignoreContentType(true)
        ignoreHttpErrors(true)
        maxBodySize(0)
        getDomainName(url).takeIf { it.isNotBlank() }?.let {
            referrer(it)
        }
    }.run {
        execute()
    }.run {
        Validate.notNull(this)
        parse()
    }
}