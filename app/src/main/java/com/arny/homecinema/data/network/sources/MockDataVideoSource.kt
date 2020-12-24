package com.arny.homecinema.data.network.sources

import android.content.Context
import com.arny.homecinema.data.models.mocks.MockMovie
import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.di.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class MockDataVideoSource(
    private val hostStore: IHostStore,
    private val context: Context
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
        get() = hostStore.baseUrl

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
            linkElem.text(),
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

    private fun readFileText(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    override fun getTitle(doc: Document): String {
        return when (hostStore.host) {
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK -> "Джокер (2019)"
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
        return@withContext readFileText(file)
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hashMapOf(
            "360" to "https://storage.videobase.xyz/6cf11987aea09f1e8029edc22f0b54d9:2020122507/movies/79d011837d439e6e52a294cf6471bbe9eaf3f429/360.mp4",
            "480" to "https://storage.videobase.xyz/6cf11987aea09f1e8029edc22f0b54d9:2020122507/movies/79d011837d439e6e52a294cf6471bbe9eaf3f429/360.mp4",
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
                element.fromJson(MockMovie::class.java)?.let { mockMovie ->
                    seasons.add(fillEposides(mockMovie))
                }
            }
        }
        seasons.sortBy { it.id }
        return SerialData(seasons)
    }

    private fun fillEposides(
        mockMovie: MockMovie,
    ): SerialSeason {
        val episodes = mutableListOf<SerialEpisode>()
        for (episodesItem in mockMovie.episodes) {
            val links = hashMapOf(
                "480" to episodesItem.hlsList.jsonMember480,
                "720" to episodesItem.hlsList.jsonMember720
            )
            val serialEpisode = SerialEpisode(
                id = episodesItem.episode.toIntOrNull() ?: 0,
                title = episodesItem.title,
                hlsList = links
            )
            episodes.add(serialEpisode)
        }
        episodes.sortBy { it.id }
        return SerialSeason(mockMovie.season, episodes)
    }
}