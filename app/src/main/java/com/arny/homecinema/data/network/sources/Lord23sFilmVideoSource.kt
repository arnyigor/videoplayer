package com.arny.homecinema.data.network.sources

import android.util.Log
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataThrowable
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.di.models.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*

@Deprecated("Blocked")
class Lord23sFilmVideoSource(
    private val hostStore: IHostStore,
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter
) : IVideoSource {

    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to hostStore.baseUrl
        ) + hostStore.baseHeaders

    override fun getSearchFields(search: String): Map<String, String> {
        return mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to search,
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

    override val searchHeaders: Map<String, String?>
        get() = mapOf(
            "Referer" to hostStore.baseUrl,
            "Origin" to hostStore.baseUrl.substringBeforeLast("/"),
        )

    override val searchUrl: String
        get() = hostStore.baseUrl

    override fun getMainPageLinks(doc: Document): Elements =
        doc.body()
            .select(".content").first()
            .select(".sect").first()
            .select(".sect-items").first()
            .select(".th-item a")

    override fun getVideoFromLink(link: Element): Movie {
        val title = link.select(".th-desc").first().select(".th-title").first().text()
        return Movie(
            UUID.randomUUID().toString(),
            title,
            MovieType.CINEMA,
            link.attr("href"),
            getImgUrl(link)
        )
    }

    private fun getImgUrl(link: Element): String =
        hostStore.baseUrl + link.select(".th-img img").first().attr("src").toString()
            .substringAfter("/")

    override fun getMenuItems(doc: Document): Elements =
        doc.body().getElementById("header").select(".hmenu li a")

    override fun getSearchResultLinks(doc: Document): Elements =
        doc.getElementById("dle-content").select(".th-item a")

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box")
            .select("iframe[src]")
            .map { it.attr("src") }
            .first { it.contains("embed") }

    override fun getTitle(doc: Document): String? {
        return ""
    }

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc
            .getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList") }
        requireNotNull(hlsList)
        return hlsList
    }

    override suspend fun getResultDoc(movie: Movie): Document {
        val body = videoApiService.getVideoDetails(movie.detailUrl, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc)
        val iFrameUrl = getIframeUrl(detailsDoc)
            ?: throw DataThrowable(R.string.video_link_not_found)
        Log.d(Lord23sFilmVideoSource::class.java.simpleName, "getResultDoc: iFrameUrl:$iFrameUrl")
        val headerHost = "api.placehere.link"
        val headers = mapOf(
            "Host" to headerHost,
            "Referer" to "https://api1589486608.placehere.link/",
        ) + hostStore.baseHeaders
        Log.d(Lord23sFilmVideoSource::class.java.simpleName, "getResultDoc: headerHost:$headerHost")
        val newIframeUrl = iFrameUrl.replace(
            "(?<=//)([\\s\\S]+?)(?=/)".toRegex(),
            headerHost
        ) + "?host=${hostStore.host}"
        Log.d(
            Lord23sFilmVideoSource::class.java.simpleName,
            "getResultDoc: newIframeUrl:$newIframeUrl"
        )
        val iFrameResponse = videoApiService.getUrlData(newIframeUrl, headers)
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
        val seasons = mutableListOf<SerialSeason>()
        hlsList.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
            .substringAfter("seasons:[{")
            .substringBefore("}]}]")
            .split("\"season\":")
            .asSequence()
            .filter { it.isNotBlank() }
            .forEach { seasonData -> fillSeason(seasonData, seasons) }
        seasons.sortBy { it.id }
        return SerialData(seasons)
    }

    private fun fillSeason(
        seasonData: String,
        seasons: MutableList<SerialSeason>
    ) {
        val seasonIdEnd = seasonData.indexOf(",\"blocked\"")
        val id = seasonData.substring(0, seasonIdEnd).toIntOrNull() ?: 0
        val episodes = mutableListOf<SerialEpisode>()
        seasonData.substringAfter("episodes\":")
            .substringAfter("[{\"")
            .substringBeforeLast("]")
            .split("episode\":\"")
            .asSequence()
            .filterNot { it.isBlank() }
            .map { it.substringBeforeLast("},{\"") }
            .forEach { episodeData -> fillEpisode(episodeData, episodes) }
        episodes.sortBy { it.id }
        seasons.add(SerialSeason(id, episodes))
    }

    private fun fillEpisode(
        episodeData: String,
        episodes: MutableList<SerialEpisode>
    ) {
        val episodeId = episodeData.substring(0, 1).toIntOrNull() ?: 0
        val videoQualityMap = hashMapOf<String, String>()
        val title = episodeData.substringAfter("\"title\":").replace("\"", "")
        episodeData
            .substringAfter("hlsList\":{")
            .substringBefore("},\"audio\"")
            .split(",")
            .asSequence()
            .map { it.substring(1, it.length - 1).replace("\"", "") }
            .forEach { hls -> fillQualityMap(hls, videoQualityMap) }
        episodes.add(
            SerialEpisode(
                episodeId,
                title,
                videoQualityMap,
                getMinQualityKey(videoQualityMap)
            )
        )
    }

    private fun getMinQualityKey(hlsQualityMap: HashMap<String, String>?): String? {
        val keys = hlsQualityMap?.keys
        return keys?.map { it.toIntOrNull() ?: 0 }?.minOrNull()?.toString() ?: keys?.first()
    }

    private fun fillQualityMap(
        hls: String,
        videoQualityMap: HashMap<String, String>
    ) {
        val quality = hls.substringBefore(":")
        val link = hls.substringAfter(":")
        if (quality.isNotBlank() && link.isNotBlank()) {
            videoQualityMap[quality] = link
        }
    }
}
