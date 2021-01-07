package com.arny.homecinema.data.network.sources

import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.MovieType
import com.arny.homecinema.di.models.SerialData
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

interface IVideoSource {
    val addMainPageHeaders: Map<String, String?>
    val searchHeaders: Map<String, String?>
    val searchUrl: String
    val detailHeaders: Map<String, String>
    fun getMainPageLinks(doc: Document): Elements
    fun getMenuItems(doc: Document): Elements
    fun getSearchResultLinks(doc: Document): Elements
    fun getIframeUrl(detailsDoc: Document): String?
    suspend fun getHlsList(doc: Document): String
    suspend fun getResultDoc(movie: Movie): Document
    fun getQualityMap(hlsList: String): HashMap<String, String>
    fun parsingSerialData(hlsList: String): SerialData
    fun getVideoFromLink(link: Element): Movie
    fun getSearchFields(search: String): Map<String, String>
    fun getMovieType(movie: Movie): MovieType
    fun getTitle(doc: Document): String?
}