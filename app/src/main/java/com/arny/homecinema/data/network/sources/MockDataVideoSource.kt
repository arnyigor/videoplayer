package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.models.SeasonItem
import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.repository.sources.assets.AssetsReader
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override val searchUrl: String
        get() = hostStore.baseUrl + "films/"

    override val searchHeaders: Map<String, String?>
        get() = mapOf(
            "Referer" to hostStore.baseUrl,
            "Origin" to hostStore.baseUrl.substringBeforeLast("/"),
        )

    override fun getVideoFromLink(link: Element): Movie {
        val short = link.select(".short-text")
        val shortImg = link.select(".short-img").first()
        val linkElem = short.first().select("a").first()
        return Movie(
            UUID.randomUUID().toString(),
            correctTitle(linkElem.text()),
            MovieType.CINEMA,
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

    override fun getIframeUrl(detailsDoc: Document): String? =
        detailsDoc.body()
            .getElementById("dle-content")
            .select(".fmain").first()
            .select(".fplayer").first()
            .select(".video-box").getOrNull(1)
            ?.select("iframe")?.attr("src")


    override fun getTitle(doc: Document): String {
        return when (hostStore.host) {
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK -> "ЧУДО-ЖЕНЩИНА: 1984 (2020)"
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK2 -> "Тайны смолвиля"
            else -> "source_1.txt"
        }
    }

    override suspend fun getResultDoc(movie: Movie): Document {
        return Document("")
    }

    override suspend fun getHlsList(doc: Document): String = withContext(Dispatchers.IO) {
        val file = when (hostStore.host) {
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK -> "source_0.txt"
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK2 -> "source_1.txt"
            else -> "source_1.txt"
        }
        return@withContext assetsReader.readFileText(file)
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hashMapOf(
            "480" to "https://m1.rumer.fun/manifest/MTc2LjExOC43OC4xNTc=/?video=https://e4ab6b73dd7dcbf79500b34cb24c0397bc418412.streamvid.club/12_27_20/12/27/05/VD4IKRG2/5NC2DHPP.mp4/tracks/v1-a/index-v1.m3u8",
        )
    }

    override fun getMovieType(movie: Movie): MovieType {
        return when (hostStore.host) {
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK -> MovieType.CINEMA
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK2 -> MovieType.SERIAL
            else -> MovieType.SERIAL
        }
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