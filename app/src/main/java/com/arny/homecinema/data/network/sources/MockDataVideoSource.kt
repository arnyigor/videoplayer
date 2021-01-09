package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.models.SeasonItem
import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.repository.sources.assets.AssetsReader
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*
import kotlin.collections.ArrayList

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
        val hlss = hlsList
            .replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), "")
            .substringAfter("hlsList:{")
            .substringBefore("}")
            .split(",")
            .map { it.substring(1, it.length - 1).replace("\"", "") }
        val videoQualityMap = hashMapOf<String, String>()
        for (hls in hlss) {
            val quality = hls.substringBefore(":")
            val link = hls.substringAfter(":")
            if (quality.isNotBlank() && link.isNotBlank()) {
                videoQualityMap[quality] = link
            }
        }
        return videoQualityMap
    }

    override fun getMovieType(movie: Movie): MovieType {
        return movie.type
    }

    override fun parsingSerialData(hlsList: String): SerialData {
        val seasons = mutableListOf<SerialSeason>()
        hlsList.fromJson(ArrayList::class.java) { jsonElement ->
            for (element in jsonElement.asJsonArray) {
                element.fromJson(SeasonItem::class.java)?.let { mockMovie ->
                    seasons.add(fillEposides(mockMovie))
                }
            }
        }
        seasons.sortBy { it.id }
        return SerialData(seasons)
    }

    private fun fillEposides(
        seasonItem: SeasonItem,
    ): SerialSeason {
        val episodes = mutableListOf<SerialEpisode>()
        for (episodesItem in seasonItem.episodes) {
            val serialEpisode = SerialEpisode(
                id = episodesItem.episode.toIntOrNull() ?: 0,
                title = episodesItem.title,
                hlsList = episodesItem.hlsList
            )
            episodes.add(serialEpisode)
        }
        episodes.sortBy { it.id }
        return SerialSeason(seasonItem.season, episodes)
    }
}