package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.models.SeasonItem
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.di.models.*
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*

class AlTabFillVideoSource(
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
        return getElements(doc, "#dle-content .mov")
    }

    override fun getSearchResultLinks(doc: Document): Elements =
        getElements(doc, "#dle-content .mov")

    override fun getMovieFromLink(link: Element): Movie {
        return Movie(
            UUID.randomUUID().toString(),
            link.select("a").attr("title"),
            MovieType.CINEMA,
            link.select("a").attr("href"),
            getImgUrl(link)
        )
    }

    private fun getImgUrl(link: Element): String {
        return imgUrl(link, ".img-box", "style", true, "url\\('(.+)'\\);".toRegex())
    }

    override fun getMenuItems(doc: Document?): Elements {
        requireNotNull(doc)
        return doc.body().select("#header .hmenu li a")
    }

    override fun getIframeUrl(detailsDoc: Document): String? {
        return getElements(detailsDoc, "#dle-content .tabsbox .vdd-element .video-box iframe")
            .getOrNull(0)
            ?.attr("src")
    }

    override suspend fun getTitle(doc: Document, movie: Movie?): String {
        return if (doc.title().isNullOrBlank()) {
            movie?.title ?: ""
        } else {
            correctTitle(doc.title())
        }
    }

    override suspend fun requestMainPage(): ResponseBody {
        return videoApiService.postRequest(
            hostStore.baseUrl,
            mapOf(
                "xsort" to "1",
                "xs_field" to "category",
                "xs_value" to "1",
            ),
            addMainPageHeaders + hostStore.mainPageHeaders
        )
    }

    override suspend fun getDetailsDoc(movie: Movie): Document {
        val body = videoApiService.getRequest(movie.detailUrl, detailHeaders)
        val detailsDoc = responseBodyConverter.convert(body)
        requireNotNull(detailsDoc) {
            "Ошибка получения деталей видео"
        }
        return detailsDoc
    }

    override suspend fun getVideoDoc(detailsDoc: Document): Document {
        val iFrameUrl = getIframeUrl(detailsDoc)
        val headers = mapOf(
            "Referer" to hostStore.baseUrl,
        ) + hostStore.baseHeaders
        val iFrameResponse = videoApiService.getRequest(iFrameUrl, headers)
        val resultDoc = responseBodyConverter.convert(iFrameResponse)
        requireNotNull(resultDoc) {
            "Ошибка получения ссылки на видео"
        }
        return resultDoc
    }

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = getElements(doc, "#nativeplayer").attr("data-config")
        val baseHlsLink = findString("hls\":\"(.+index.m3u8)\"".toRegex(), hlsList)
        requireNotNull(hlsList) {
            "Ошибка получения ссылки на видео"
        }
        val headers = mapOf(
            "Referer" to "https://vid1610061656.vb17120ayeshajenkins.pw/",
        ) + hostStore.baseHeaders
        val hlsLink = "https:" + baseHlsLink.replace("\\/", "/")
        val iFrameResponse = videoApiService.getRequest(hlsLink, headers)
        val resultDoc = responseBodyConverter.convert(iFrameResponse, true)
        requireNotNull(resultDoc) {
            "Ошибка получения ссылки на видео"
        }
        val element = getElements(resultDoc, "script").first().data()
        val qualities =
            "./(\\d+)/index".toRegex().findAll(element).toList().map { it.groupValues[1] }
        val linkForMap =
            "https://cdn4502." + hlsLink.substringBefore("index.m3u8").substringAfter(".")
        val qualityBuilder = StringBuilder()
        qualityBuilder.append("hlsList:{")
        require(qualities.isNotEmpty()) {
            "Ошибка получения видео качества"
        }
        for (withIndex in qualities.withIndex()) {
            val index = withIndex.index
            if (index != 0) {
                qualityBuilder.append(",")
            }
            qualityBuilder.append("\"${withIndex.value}\":\"${linkForMap}${withIndex.value}/index.m3u8\"")
        }
        qualityBuilder.append("}")
        return qualityBuilder.toString()
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
        val seasonsStringData = hlsList.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
            .substringAfter("seasons:[{")
            .substringBefore("}]}]")
        val result = "[{$seasonsStringData}]}]"
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
