package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.di.models.*
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.nio.charset.Charset
import java.util.*

class OnlineVKinoVideoSource(
    private val hostStore: IHostStore,
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter
) : BaseVideoSource(hostStore), IVideoSource {

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
            "titleonly" to "3",
        )
    }

    override fun getMovieType(movie: Movie): MovieType {
        val link = movie.detailUrl?.substringAfter("//")?.substringAfter("/") ?: ""
        return when {
            link.contains("sezon") -> MovieType.SERIAL
            else -> MovieType.CINEMA
        }
    }

    override suspend fun getMainPageLinks(doc: Document?): Elements {
        requireNotNull(doc)
        return doc.select("#dle-content .shortstory")
    }

    override fun getMovieFromLink(link: Element): Movie {
        val aLink = link.select(".shortstorytitle h2 a")
        return Movie(
            UUID.randomUUID().toString(),
            aLink.text(),
            MovieType.CINEMA,
            aLink.attr("href"),
            imgUrl(link, ".shortimg img", "src", false)
        )
    }

    override fun getMenuItems(doc: Document?): Elements {
        requireNotNull(doc)
        return doc.body().select(".leftblok_contener .leftblok_contener2 a")
    }

    override fun getCharset(): Charset {
        return Charset.forName("windows-1251")
    }

    override fun getMenuVideoLink(link: Element): VideoMenuLink {
        return VideoMenuLink(link.text(), link.attr("href"))
    }

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.select("#dle-content .shortstory")

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .select("#dle-content iframe")?.attr("src")

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc.getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData.clearSymbols() }
            .find { it.contains("hlsList\"?:\\s*\\{\\s*\"\\d+".toRegex()) }
        requireNotNull(hlsList)
        return hlsList
    }

    override suspend fun getTitle(doc: Document, movie: Movie?): String {
        return correctTitle(doc.title())
    }

    override suspend fun requestMainPage(): ResponseBody {
        return videoApiService.getRequest(
            hostStore.baseUrl,
            addMainPageHeaders + hostStore.mainPageHeaders
        )
    }

    override suspend fun getDetailsDoc(movie: Movie): Document {
        val body = videoApiService.getRequest(movie.detailUrl, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body, charset = getCharset())
        requireNotNull(detailsDoc)
        return detailsDoc
    }

    override suspend fun getVideoDoc(detailsDoc: Document): Document {
        val iFrameUrl = getIframeUrl(detailsDoc)
        val newHost = "api.multikland.net"
        val headers = mapOf(
            "Host" to newHost,
            "Referer" to getReferer(iFrameUrl)
        ) + hostStore.baseHeaders
        val iFrameResponse = videoApiService.getRequest(
            correctedIFragmeUrl(iFrameUrl, newHost, hostStore.host),
            headers
        )
        val resultDoc = responseBodyConverter.convert(iFrameResponse, charset = Charsets.UTF_8)
        requireNotNull(resultDoc)
        return resultDoc
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hlsList.clearSymbols()
            .substringAfterBefore("hlsList: {", "}")
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
