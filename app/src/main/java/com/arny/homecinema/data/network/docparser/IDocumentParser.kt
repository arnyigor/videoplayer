package com.arny.homecinema.data.network.docparser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

interface IDocumentParser {
    fun getMainPageLinks(doc: Document): Elements
    fun getImgUrl(link: Element, baseUrl: String): String
    fun getMenuItems(doc: Document): Elements
    fun getSearchResultLinks(doc: Document): Elements
    fun getIframeUrl(detailsDoc: Document): String?
    fun getHlsList(doc: Document): String
}