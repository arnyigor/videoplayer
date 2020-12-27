package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.models.SeasonItem
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.di.models.*
import org.joda.time.DateTime
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*

class KinoIOnlineVideoSource(
    private val hostStore: IHostStore,
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter
) : IVideoSource {

    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to hostStore.baseUrl
        )

    override fun getMainPageLinks(doc: Document): Elements =
        doc.body()
            .getElementById("cols")
            .getElementById("grid")
            .getElementById("dle-content")
            .select(".short")

    override fun getSearchFields(search: String): Map<String, String> {
        return mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to search,
        )
    }

    override fun getMovieType(movie: Movie): MovieType {
        val link = movie.detailUrl ?: ""
        return when {
            movie.type == MovieType.SERIAL -> MovieType.SERIAL
            link.contains("seriya") && link.contains("sezon") -> MovieType.SERIAL
            else -> MovieType.CINEMA
        }
    }

    override val searchUrl: String
        get() = hostStore.baseUrl

    override val searchHeaders: Map<String, String?>
        get() = mapOf(
            "Referer" to hostStore.baseUrl,
            "Origin" to hostStore.baseUrl.substringBeforeLast("/"),
        )

    override fun getVideoFromLink(link: Element): Movie {
        val shortText = link.select(".short-text")
        val shortImg = link.select(".short-img").first()
        val linkElem = shortText.first().select("a").first()
        val type = when {
            shortText.select(".short-desc").first().toString()
                .contains("Серии:") -> MovieType.SERIAL
            else -> MovieType.CINEMA
        }
        return Movie(
            UUID.randomUUID().toString(),
            correctTitle(linkElem.text()),
            type,
            linkElem.attr("href"),
            hostStore.baseUrl + shortImg.select("img")
                .first().attr("src")
                .toString().substringAfter("/")
        )
    }

    override fun getMenuItems(doc: Document): Elements =
        doc.body().getElementById("header").select(".hmenu li a")

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.body()
            .getElementById("cols")
            .select(".main").first()
            .getElementById("dle-content")
            .select(".short")

    override fun getIframeUrl(detailsDoc: Document): String? {
        val body = detailsDoc.body()
            .select("#dle-content").first()
        val links = body.select(".fplayer").first()
            .select(".video-box iframe").map { it.attr("src") }
        val firstOrNull =
            body.select("script").map { it.data() }.firstOrNull { it.contains(",re=") }
        val replaseWith = regexBetwenTwoString(
            "actual = \"",
            "\""
        ).find(firstOrNull.toString())?.groupValues?.getOrNull(0)
        val pattern =
            regexBetwenTwoString(
                ",re=",
                ",delay"
            ).find(firstOrNull.toString())?.groupValues?.getOrNull(0)
                ?.replace("\n", "")?.replace("\t", "")
        return if (pattern != null && replaseWith != null) {
            val replaseWhat = pattern.replace("^/".toRegex(), "").replace("/$".toRegex(), "")
            val regex = replaseWhat.toRegex()
            val find = links.find { it.contains(regex) }
            return find?.replace(regex, replaseWith) ?: links.first()
        } else {
            links.first()
        }
    }

    override fun getTitle(doc: Document): String {
        return correctTitle(doc.title())
    }

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc
            .getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList.toString().replace("\n", "").replace("\t", "").replace("\\s".toRegex(), " ")
    }

    override suspend fun getResultDoc(movie: Movie): Document {
        val detailUrl = movie.detailUrl
        val url = if (movie.type == MovieType.CINEMA) {
            if (!".+-\\d{4}-\\d{2}-\\d{2}-\\d{2}.html$".toRegex().matches(detailUrl ?: "")) {
                val extent = detailUrl?.substringAfterLast(".")
                val baseUrl = detailUrl?.substringBeforeLast(".")
                val time = DateTime.now().toString("-yyyy-MM-dd-HH")
                "#$baseUrl.$time.$extent"
            } else {
                detailUrl
            }
        } else {
            detailUrl
        }
        val body = videoApiService.getVideoDetails(url, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
        val iFrameResponse = videoApiService.getUrlData(
            iFrameUrl,
            hostStore.baseHeaders
        )
        val resultDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(resultDoc)
        return resultDoc
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

    override fun parsingSerialData(hlsList: String): SerialData {
        val substringBefore = hlsList.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
            .substringAfter("seasons:[{")
            .substringBefore("}]}]")
        val result = "[{$substringBefore}]}]"
        val seasons = mutableListOf<SerialSeason>()
        result.fromJson(ArrayList::class.java) { jsonElement ->
            for (element in jsonElement.asJsonArray) {
                element.fromJson(SeasonItem::class.java)?.let { movie ->
                    seasons.add(fillEposides(movie))
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
