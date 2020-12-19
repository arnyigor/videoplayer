package com.arny.homecinema.data.network.docparser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class AlLordFilmDocumentParser : IDocumentParser {
    override fun getMainPageLinks(doc: Document): Elements =
        doc.body()
            .select(".content").first()
            .select(".sect").first()
            .select(".sect-items").first()
            .select(".th-item a")

    override fun getImgUrl(link: Element, baseUrl: String): String =
        link.select(".th-img").first().select("img").first().attr("src").toString()

    override fun getMenuItems(doc: Document): Elements =
        doc.body().getElementById("header").select(".hmenu li a")

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.getElementById("dle-content").select(".th-item a")

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box").getOrNull(1)
            ?.select("iframe")?.attr("src")

    override fun getHlsList(doc: Document): String {
        val hlsList = doc
            .getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList
    }
}
