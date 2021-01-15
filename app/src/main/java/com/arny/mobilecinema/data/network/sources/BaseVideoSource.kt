package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.IHostStore
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

open class BaseVideoSource(
    private val hostStore: IHostStore
) {
    fun getElements(doc: Document, selector: String): Elements {
        return doc.select(selector)
    }

    fun imgUrl(
        link: Element,
        selector: String,
        attr: String,
        addBaseUrl: Boolean = true,
        additionalRegex: Regex? = null
    ): String {
        val attrLink = link.select(selector).attr(attr).toString()
        val href = additionalRegex?.let {
            findString(it, attrLink)
        } ?: attrLink
        return if (addBaseUrl) {
            hostStore.baseUrl + (href.substringAfter("/"))
        } else {
            href
        }
    }

    fun getReferer(url: String?): String {
        if (url == null) return ""
        return "^https?://([-.0-9a-z]+)".toRegex()
            .find(url)?.groupValues
            ?.getOrNull(0).toString() + "/"
    }

    fun correctedIFragmeUrl(
        url: String?,
        hostToReplace: String? = null,
        baseHost: String? = null
    ): String {
        if (url == null) return ""
        val corrected = if (hostToReplace == null) {
            url
        } else {
            url.replace(
                "^https?://([-.0-9a-z]+)".toRegex()
                    .find(url)?.groupValues
                    ?.getOrNull(1).toString(),
                hostToReplace
            )
        }
        return baseHost?.let { "$corrected?host=$it" } ?: corrected
    }

    fun findString(it: Regex, pattern: String, group: Int = 1) =
        it.find(pattern)?.groupValues?.getOrNull(group).toString()

    fun findStrings(it: Regex, pattern: String) =
        it.find(pattern)?.groupValues
}
