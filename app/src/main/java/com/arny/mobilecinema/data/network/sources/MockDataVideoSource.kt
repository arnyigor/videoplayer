package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.HostStoreImpl
import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReader
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.SerialData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*

class MockDataVideoSource(
    private val hostStore: IHostStore,
    private val assetsReader: AssetsReader
) : IVideoSource {
    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to hostStore.baseUrl
        )

    override val addMainPageHeaders: Map<String, String?>
        get() = emptyMap()

    override suspend fun getMainPageLinks(doc: Document?): Elements {
        return withContext(Dispatchers.IO) {
            val file = when (hostStore.host) {
                HostStoreImpl.HOST_MOCK -> "demo/links.txt"
                else -> "demo/links.txt"
            }
            Jsoup.parse(assetsReader.readFileText(file)).body().select("a")
        }
    }

    override fun getSearchFields(search: String): Map<String, String> {
        return mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to search,
        )
    }

    override val searchUrl: String
        get() = hostStore.baseUrl + "films/"

    override val searchHeaders: Map<String, String?>
        get() = mapOf(
            "Referer" to hostStore.baseUrl,
            "Origin" to hostStore.baseUrl.substringBeforeLast("/"),
        )

    override fun getMovieFromLink(link: Element): Movie {
        val type = when (link.attr("type")) {
            "serial" -> MovieType.SERIAL
            "cinema" -> MovieType.CINEMA
            else -> MovieType.CINEMA
        }
        return Movie(
            UUID.randomUUID().toString(),
            correctTitle(link.text()),
            type,
            link.attr("href"),
            link.attr("href")
        )
    }

    override fun getMenuItems(doc: Document?): Elements = Elements()

    override fun getSearchResultLinks(doc: Document): Elements = Elements()

    override fun getIframeUrl(detailsDoc: Document): String = ""

    override suspend fun getTitle(doc: Document, movie: Movie?): String {
        return movie?.title ?: ""
    }

    override suspend fun getResultDoc(movie: Movie): Document {
        return withContext(Dispatchers.IO) {
            val readFileText = assetsReader.readFileText("demo/links.txt")
            val linksDoc = Jsoup.parse(readFileText)
            val links = linksDoc.select("a").map { it.attr("href").toString() }
            val index = links.indexOf(movie.detailUrl)
            val fileData = assetsReader.readFileText("demo/source_$index.txt")
            Jsoup.parse("<script>$fileData</script>")
        }
    }

    override suspend fun getHlsList(doc: Document): String = withContext(Dispatchers.IO) {
        val hlsList = doc.getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        hlsList
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hlsList.clearSymbols()
            .substringAfterBefore("hlsList:{", "}")
            .split(",")
            .map { it.substring(1, it.length - 1).replace("\"", "") }
            .toMap()
    }

    override fun getMovieType(movie: Movie): MovieType = movie.type

    override fun parsingSerialData(hlsList: String): SerialData = hlsList.parseSerialData()
}