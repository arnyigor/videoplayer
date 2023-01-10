package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.SerialData
import com.arny.mobilecinema.data.api.VideoApiService
import com.arny.mobilecinema.di.models.VideoMenuLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.nio.charset.Charset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WrapApiSource @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter,
) : IVideoSource {
    companion object {
        const val REFRESH_TIME_MIN = 5 * 60 * 1000
    }

    private var lastSaveDataTime = 0L
    private var data: MoviesData? = null
    override val addMainPageHeaders: Map<String, String?>
        get() = emptyMap()
    override val searchHeaders: Map<String, String?>
        get() = emptyMap()
    override val searchUrl: String
        get() = ""
    override val detailHeaders: Map<String, String>
        get() = emptyMap()

    private fun isNeedUpdateData(): Boolean {
        return when (data) {
            null -> true
            else -> {
                val currentTime = System.currentTimeMillis()
                lastSaveDataTime != 0L && currentTime - lastSaveDataTime >= REFRESH_TIME_MIN
            }
        }
    }

    override fun resetRefreshTime() {
        data = null
        lastSaveDataTime = 0
    }

    override suspend fun getMainPageLinks(doc: Document?): Elements {
        if (isNeedUpdateData()) {

            lastSaveDataTime = System.currentTimeMillis()
        }
        return Elements(data?.mock?.map {
            Jsoup.parse("<a type=\"${it?.type}\" href=\"${it?.img}\">${it?.title}</a>")
        }).select("a")
    }

    override fun getMenuItems(doc: Document?): Elements {
        return Elements()
    }

    override fun getSearchResultLinks(doc: Document): Elements {
        return Elements()
    }

    override fun getIframeUrl(detailsDoc: Document): String? {
        return ""
    }

    override suspend fun getHlsList(doc: Document): String {
        val hlsList = doc.getElementsByTag("script")
            .dataNodes()
            .map { it.wholeData }
            .find { it.contains("hlsList\"?\\s*:\\s*\\{\\s*\"\\d+".toRegex()) }
        requireNotNull(hlsList)
        return hlsList
    }

    override suspend fun getDetailsDoc(movie: Movie): Document {
        val episodesString = data?.mock?.find { it?.title == movie.title }?.seasons.toString()
        val episodes =
            episodesString.substring(1, episodesString.length - 1).replace("\\s+".toRegex(), "")
        return Jsoup.parse("<script>${episodes}</script>")
    }

    override suspend fun getVideoDoc(detailsDoc: Document): Document {
        return detailsDoc
    }

    override fun getQualityMap(hlsList: String): HashMap<String, String> {
        return hashMapOf()
    }

    override fun parsingSerialData(hlsList: String): SerialData {
        return hlsList.parseSerialData()
    }

    override fun getMovieFromLink(link: Element): Movie {
        val type = when (link.attr("type")) {
            "serial" -> MovieType.SERIAL
            "cinema" -> MovieType.CINEMA
            else -> MovieType.CINEMA
        }
        return Movie(
            uuid = UUID.randomUUID().toString(),
            title = correctTitle(link.text()),
            type = type,
            detailUrl = link.attr("href"),
            img = link.attr("href")
        )
    }

    override fun getSearchFields(search: String): Map<String, String> {
        return emptyMap()
    }

    override fun getMovieType(movie: Movie): MovieType {
        return movie.type
    }

    override suspend fun getTitle(doc: Document, movie: Movie?): String {
        return movie?.title.orEmpty()
    }

    override suspend fun requestMainPage(): Document {
        return Document("")
    }

    override fun getMenuVideoLink(link: Element): VideoMenuLink {
        return VideoMenuLink()
    }

    override fun getCharset(): Charset {
        return Charsets.UTF_8
    }
}