package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.models.SeasonItem
import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.di.models.*
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
        return doc.body()
            .select(".content").first()
            .select(".sect").first()
            .select(".sect-items").first()
            .select(".th-item a")
    }


    override fun getMovieFromLink(link: Element): Movie {
        return Movie(
            UUID.randomUUID().toString(),
            link.text(),
            MovieType.CINEMA,
            link.attr("href"),
            getImgUrl(link)
        )
    }

    private fun getImgUrl(link: Element): String =
        link.select(".th-img").first().select("img").first().attr("src").toString()

    override fun getMenuItems(doc: Document?): Elements{
        requireNotNull(doc)
        return doc.body().getElementById("header").select(".hmenu li a")
    }

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.getElementById("dle-content").select(".th-item a")

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box").getOrNull(1)
            ?.select("iframe")?.attr("src")

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc.getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList
    }

    override suspend fun getTitle(doc: Document, movie: Movie?): String? {
        return doc.title()
    }

    override suspend fun getResultDoc(movie: Movie): Document {
        val body = videoApiService.getVideoDetails(movie.detailUrl, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
        val headers = mapOf(
            "Host" to "apilordfilms-s.multikland.net",
        ) + hostStore.baseHeaders
        val iFrameResponse = videoApiService.getUrlData(
            iFrameUrl,
            headers
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
