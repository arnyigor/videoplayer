package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.SerialData
import com.arny.mobilecinema.di.models.VideoApiService
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*

class AlLordFilmVideoSource(
    private val hostStore: IHostStore,
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter
) : IVideoSource {

    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to hostStore.baseUrl,
            "Host" to (hostStore.host ?: ""),
        )

    override val addMainPageHeaders: Map<String, String?>
        get() = emptyMap()

    override val searchHeaders: Map<String, String?>
        get() = mapOf(
            "Host" to hostStore.host,
            "Referer" to hostStore.baseUrl,
            "Origin" to hostStore.baseUrl.substringBeforeLast("/"),
        )

    override val searchUrl: String
        get() = hostStore.baseUrl

    override fun getSearchFields(search: String): Map<String, String> {
        return mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to search,
            "search_start" to "0",
            "full_search" to "0",
            "result_from" to "1",
        )
    }

    override fun getMovieType(movie: Movie): MovieType {
        val link = movie.detailUrl?.substringAfter("//")?.substringAfter("/") ?: ""
        return when {
            link.contains("-film-") -> MovieType.CINEMA
            link.contains("-serial-") -> MovieType.SERIAL
            else -> MovieType.CINEMA
        }
    }

    override suspend fun getMainPageLinks(doc: Document?): Elements {
        requireNotNull(doc)
        return doc.body().select(".content .sect .sect-items .th-item a")
    }


    override fun getMovieFromLink(link: Element): Movie {
        val title = link.select(".th-desc .th-title").text()
        val year = link.select(".th-desc .th-year")
            .text().takeIf { !it.isNullOrBlank() }?.let {
                " ($it)"
            } ?: ""
        return Movie(
            UUID.randomUUID().toString(),
            "$title${year}",
            MovieType.CINEMA,
            link.attr("href"),
            getImgUrl(link)
        )
    }

    private fun getImgUrl(link: Element): String =
        link.select(".th-img img").attr("src").toString()

    override fun getMenuItems(doc: Document?): Elements {
        requireNotNull(doc)
        return doc.select("#header .hmenu li a")
    }

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.select("#dle-content .th-item a")

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .select("#dle-content .fmain .fplayer .video-box iframe")
            .getOrNull(1)?.attr("src")

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc.getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList
    }

    override suspend fun getTitle(doc: Document, movie: Movie?): String? {
        if (movie?.title.isNullOrBlank()) {
            return doc.title()
        }
        return movie?.title
    }

    override suspend fun requestMainPage(): ResponseBody {
        return videoApiService.getRequest(
            hostStore.baseUrl,
            addMainPageHeaders + hostStore.mainPageHeaders
        )
    }

    override suspend fun getDetailsDoc(movie: Movie): Document {
        val body = videoApiService.getRequest(movie.detailUrl, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        return detailsDoc
    }

    override suspend fun getVideoDoc(detailsDoc: Document): Document {
        val iFrameUrl = getIframeUrl(detailsDoc)
        val headers = mapOf(
            "Host" to "apilordfilms-s.multikland.net",
        ) + hostStore.baseHeaders
        val iFrameResponse = videoApiService.getRequest(
            iFrameUrl,
            headers
        )
        val resultDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(resultDoc)
        return resultDoc
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hlsList.clearSymbols()
            .substringAfterBefore("hlsList:{", "}")
            .split(",")
            .map { it.substring(1, it.length - 1).replace("\"", "") }
            .toMap()
    }

    override fun parsingSerialData(hlsList: String): SerialData {
        return hlsList.clearSymbols(true)
            .substringAfterBefore("seasons:[{", "}]}]")
            .getParsingString("[{", "}]}]")
            .parseSerialData()
    }
}
