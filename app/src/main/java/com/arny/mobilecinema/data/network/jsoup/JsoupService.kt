package com.arny.mobilecinema.data.network.jsoup

import com.arny.mobilecinema.data.utils.getDomainName
import org.jsoup.helper.Validate
import org.jsoup.nodes.Document

class JsoupService private constructor() {
    companion object {
        fun getInstance() = JsoupService()
        private const val UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    }

    fun loadPage(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        logLevel: LogLevel = LogLevel.NONE,
        timeout: Int = 10000
    ): Document = JsoupLoggerConnection.connect(url, logLevel).apply {
        userAgent(UA)
        headers(requestHeaders)
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