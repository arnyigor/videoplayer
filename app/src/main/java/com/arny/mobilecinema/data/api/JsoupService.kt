package com.arny.mobilecinema.data.api

import com.arny.mobilecinema.data.utils.getDomainName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class JsoupService private constructor() {

    companion object {
        fun getInstance() = JsoupService()
        private const val UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        private const val TIMEOUT_MS = 1000 * 10 // sec
    }

    fun loadPage(
        url: String,
        requestHeaders: Map<String, String>? = null,
        currentProxy: Pair<String, Int>? = null
    ): Document = Jsoup.connect(url).apply {
        userAgent(UA)
        currentProxy?.let { (address, port) -> proxy(address, port) }
        requestHeaders?.let { headers(it) }
        timeout(TIMEOUT_MS)
        followRedirects(true)
        ignoreContentType(true)
        ignoreHttpErrors(true)
        maxBodySize(0)
        getDomainName(url).takeIf { it.isNotBlank() }?.let {
            referrer(it)
        }
    }.get()
}