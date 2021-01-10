package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.network.hosts.IHostStore
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

    fun findString(it: Regex, pattern: String, group: Int = 1) =
        it.find(pattern)?.groupValues?.getOrNull(group).toString()

    fun findStrings(it: Regex, pattern: String) =
        it.find(pattern)?.groupValues
}
