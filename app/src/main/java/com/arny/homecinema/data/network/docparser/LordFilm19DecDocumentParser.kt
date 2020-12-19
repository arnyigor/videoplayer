package com.arny.homecinema.data.network.docparser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class LordFilm19DecDocumentParser(
    private val host: String?
) : IDocumentParser {
    override fun getMainPageLinks(doc: Document): Elements =
        doc.body()
            .select(".content").first()
            .select(".sect").first()
            .select(".th-item a")

    override fun getImgUrl(link: Element, baseUrl: String): String =
        baseUrl + link.select(".th-img").first()
            .select("img").first().attr("src").toString()
            .substringAfter("/")

    override fun getMenuItems(doc: Document): Elements =
        doc.body().getElementById("header").select(".hmenu li a")

    override fun getSearchResultLinks(doc: Document): Elements {
        return doc.select(".content").select(".sect a")
    }

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box").getOrNull(1)
            ?.select("iframe")?.attr("src")

    override fun getHlsList(doc: Document): String {
        val data = doc
            .getElementById("nativeplayer")
            .attr("data-config").toString()
        val find = "hls\":\"(.*)m3u8\"".toRegex().find(data)
        val hlsList = find?.groups?.get(1)?.value
        requireNotNull(hlsList)
        return hlsList
    }
}
