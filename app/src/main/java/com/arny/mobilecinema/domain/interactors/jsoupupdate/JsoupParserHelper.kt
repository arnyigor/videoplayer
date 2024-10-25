package com.arny.mobilecinema.domain.interactors.jsoupupdate

import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.readSeasons
import com.arny.mobilecinema.data.utils.cleanAnwapEncryptedData
import com.arny.mobilecinema.data.utils.cleanEmptySymbols
import com.arny.mobilecinema.data.utils.fromJsonToList
import com.arny.mobilecinema.data.utils.getDecodedData
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.data.utils.getDurationTimeSec
import com.arny.mobilecinema.data.utils.getTime
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.LoadingData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialSeason
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.math.BigDecimal

fun String.removeDomain(): String =
    this.replace(getDomainName(this) + "/", "")

fun loading(
    params: Pair<String, String>,
    complete: Boolean = false,
    success: Boolean = false
) = DataResultWithProgress.Progress(
    LoadingData(mapOf(params), complete, success)
)

fun loading(
    params: Map<String, String>,
    complete: Boolean = false,
    success: Boolean = false
) = DataResultWithProgress.Progress(
    LoadingData(params, complete, success)
)

fun String.inlineText(): String =
    replace("\t", "")
        .replace("\n", "")
        .replace("\\s{2,}", " ")
        .trim()

fun getPagesCount(page: Document) =
    page.select(Selectors.ALL_PAGES).text().toIntOrNull() ?: 1

fun getPageUrl(baseUrl: String, page: Int, parseType: String): String {
    val s = when (parseType) {
        Selectors.TYPE_FILMS -> "films"
        else -> "serials"
    }
    return "${baseUrl}/$s/p-$page"
}

fun getFilmLinks(page: Document, location: String): List<String> {
    val select = page.select(Selectors.FILM_LINK)
    val map = select.map {
        it.attr(Selectors.FILM_LINK_ATTR).orEmpty().getWithDomain(location)
    }
    return map
}

fun Movie.hasAllVideoData(): Boolean {
    return when (type) {
        MovieType.NO_TYPE -> false
        MovieType.CINEMA -> {
            cinemaUrlData?.let { !it.isNoCinemaUrls() } ?: false
        }

        MovieType.SERIAL -> {
            seasons.isNotEmpty() && seasons.all { it.episodes.isNotEmpty() }
        }
    }
}

fun CinemaUrlData.isNoCinemaUrls(): Boolean {
    val cinemaUrls = cinemaUrl?.urls
    val hdUrls = hdUrl?.urls
    val allEmpty = cinemaUrls.isNullOrEmpty() && hdUrls.isNullOrEmpty()
    val strings = cinemaUrls.orEmpty() + hdUrls.orEmpty()
    return allEmpty || filterTrailer(strings).isEmpty()
}

fun getUpdateTime(dateInfo: String, updateToNow: Boolean): Long {
    var dateTime: DateTime?
    val zone = DateTimeZone.forID("Europe/Moscow")
    dateTime = DateTime(zone).withMillisOfDay(0)
    dateTime = when {
        updateToNow -> DateTime.now()
        dateInfo.contains("вчера", ignoreCase = true) -> {
            dateTime = dateTime.minusDays(1)
            val time = findByGroup(dateInfo, "вчера\\s(.+)$".toRegex(), 1).orEmpty()
            val timeList = time.split(":")
            val h = timeList.getOrNull(0)?.toInt()!!
            val m = timeList.getOrNull(1)?.toInt()!!
            dateTime.withHourOfDay(h).withMinuteOfHour(m)
        }

        ("^\\d{2}:\\d{2}$".toRegex().find(dateInfo)?.value != null) -> {
            val timeList = dateInfo.split(":")
            val h = timeList.getOrNull(0)?.toInt()!!
            val m = timeList.getOrNull(1)?.toInt()!!
            dateTime.withHourOfDay(h).withMinuteOfHour(m)
        }

        else -> getTime("YYYY.MM.dd HH:mm", dateInfo).withZone(zone)
    }
//    Timber.d("getUpdateTime dateInfo:$dateInfo dateTime:${dateTime.millis.printTime()}")
    return dateTime!!.millis
}

fun getRatingData(ratingText: String): Pair<Double?, Double?> {
    val ratingList = ratingText.split("|")
    var imdbRating: Double? = null
    var kpRating: Double? = null
    ratingList.getOrNull(0)?.trim()?.let {
        val imdbRatList = it.split("/")
        if (imdbRatList.isNotEmpty() && imdbRatList.size > 1) {
            val s = imdbRatList[0].trim()
            val from = BigDecimal(s).toDouble()
            imdbRating = from
        }
    }
    ratingList.getOrNull(1)?.trim()?.let {
        val kpRatList = it.split("/")
        if (kpRatList.isNotEmpty() && kpRatList.size > 1) {
            val s = kpRatList[0].trim()
            kpRating = BigDecimal(s).toDouble()
        }
    }
    return imdbRating to kpRating
}

fun getInfo(page: Element, updateToNow: Boolean): MovieInfo {
    var updated: Long = 0
    var year = 0
    var origTitle = ""
    var quality = ""
    var translate = ""
    var durationSec = 0
    var age: Int = -1
    var countries: List<String> = emptyList()
    var genre: List<String> = emptyList()
    var ratingImdb: Double = -1.0
    var ratingKP: Double = -1.0
    var directors: List<String> = emptyList()
    var actors: List<String> = emptyList()
    val infoList = page.select(Selectors.INFO).map { it.wholeText().cleanEmptySymbols() }
    for (info in infoList) {
        when {
            info.startsWith("Год") -> {
                year = findByGroup(info, "Год:(\\d+)".toRegex(), 1)?.toInt()!!
            }

            info.startsWith("Обновлен")
                    || info.startsWith("Добавлен", ignoreCase = true) -> {
                val dateInfo =
                    findByGroup(info, "(Обновлен:|Добавлен:|Добавлена:)(.*)$".toRegex(), 2)!!.trim()
                updated = getUpdateTime(dateInfo, updateToNow)
            }

            info.startsWith("Оригинал") -> {
                origTitle = findByGroup(info, "Оригинал:(.*)".toRegex(), 1).orEmpty().trim()
            }

            info.startsWith("Перевод") -> {
                translate = findByGroup(info, "Перевод:(.*)".toRegex(), 1).orEmpty().trim()
            }

            info.startsWith("Время") -> {
                val durationText = findByGroup(info, "Время:(.*)".toRegex(), 1).orEmpty().trim()
                durationSec = getDurationTimeSec(durationText)
            }

            info.startsWith("Возраст") -> {
                age = findByGroup(info, "Возраст:(\\d+)+".toRegex(), 1)?.toInt()!!
            }

            info.startsWith("Страна") -> {
                val countriesText = findByGroup(info, "Страна:(.*)$".toRegex(), 1).orEmpty()
                countries = countriesText.split(",").map { it.trim() }
            }

            info.startsWith("Жанр") -> {
                val countriesText = findByGroup(info, "Жанр:(.*)$".toRegex(), 1).orEmpty()
                genre = countriesText.split(",").map { it.trim() }
            }

            info.startsWith("Качество") -> {
                quality = findByGroup(info, "Качество:(.*)$".toRegex(), 1).orEmpty()
            }

            info.startsWith("Режиссер") -> {
                val directorsText = findByGroup(info, "Режиссер:(.*)$".toRegex(), 1).orEmpty()
                directors = directorsText.split(",").map { it.trim() }
            }

            info.startsWith("Актеры") -> {
                val directorsText = findByGroup(info, "Актеры:(.*)$".toRegex(), 1).orEmpty()
                actors = directorsText.split(",").map { it.trim() }
            }

            info.startsWith("Рейтинг") -> {
                val ratingText = findByGroup(info, "Рейтинг:(.*)$".toRegex(), 1).orEmpty()
                val (rImdb, rKp) = getRatingData(ratingText)
                ratingImdb = rImdb ?: -1.0
                ratingKP = rKp ?: -1.0
            }
        }
    }
    val (likes, dislikes) = getAnwapRating(page)
    val description = getFullDescription(page)
    return MovieInfo(
        updated = updated,
        origTitle = origTitle,
        year = year,
        quality = quality,
        translate = translate,
        durationSec = durationSec,
        age = age,
        countries = countries,
        genres = genre,
        likes = likes,
        dislikes = dislikes,
        ratingImdb = ratingImdb,
        ratingKP = ratingKP,
        directors = directors,
        actors = actors,
        description = description
    )
}

private fun getAnwapRating(page: Element): Pair<Int, Int> {
    val map = page.select(Selectors.RATING).map { it.wholeText() }
    if (map.isNotEmpty()) {
        val like = map[0].trim().toInt()
        val dislike = map[1].trim().toInt()
        return like to dislike
    }
    return 0 to 0
}

fun findByGroup(input: String, regex: Regex, group: Int): String? =
    regex.find(input)?.groupValues?.getOrNull(group)

fun findAllByGroup(input: String, regex: Regex, group: Int): List<String> =
    regex.findAll(input).toList().map { it.groups[group]?.value.orEmpty() }

fun getFullDescription(page: Element): String {
    val selectFirst = page.selectFirst(Selectors.DESCRIPTION)
    return selectFirst?.text()?.replace("(?i)<br[^>]*>".toRegex(), "").orEmpty()
}

fun getMovieId(location: String): Int =
    findByGroup(location, "(films|serials)/(\\d+)".toRegex(), 2)?.toIntOrNull() ?: 0

fun getMovieType(location: String): MovieType =
    when ("(films|serials)".toRegex().find(location)?.value) {
        "films" -> MovieType.CINEMA
        "serials" -> MovieType.SERIAL
        else -> MovieType.NO_TYPE
    }

fun getTitle(page: Element): String = page.selectFirst(Selectors.TITLE)?.text().orEmpty()

fun getImg(
    page: Element,
    location: String
): String {
    val img = page.selectFirst(Selectors.IMG)
        ?.attr(Selectors.SRC_ATTR).orEmpty()
    val url = img.getWithDomain(location)
    return url.removeDomain()
}

fun getCinemaUrlData(
    page: Element
): CinemaUrlData {
    val scriptData =
        page.selectFirst(Selectors.PAGE_SCRIPT)?.data().orEmpty().trimIndent().trim()
    val cinemaUrl = getUrlsData(
        scriptData = scriptData,
        regex = Selectors.KINO_REGEXP.toRegex(),
        simpleRegex = Selectors.SIMPLE_REGEXP.toRegex(),
        require = true,
    )
    return CinemaUrlData(cinemaUrl = cinemaUrl)
}

fun getSeriyaUrlData(
    page: Element
): List<String> {
    val scriptData =
        page.selectFirst(Selectors.PAGE_SCRIPT)?.data().orEmpty().trimIndent().trim()
    return getUrlsData(
        scriptData = scriptData,
        regex = Selectors.KINO_REGEXP.toRegex(),
        simpleRegex = Selectors.SIMPLE_REGEXP.toRegex(),
        require = true,
        fileEnds = "(.txt)"
    ).urls
}

fun getHdUrl(
    page: Element
): String {
    val data = page.selectFirst(Selectors.KINO_HD_SCRIPT)?.data().orEmpty().trimIndent().trim()
    val url = if (data.isNotBlank()) {
        findByGroup(data, Selectors.IFRAME_SRC_REGEXP.toRegex(), 1).orEmpty()
    } else {
        page.selectFirst(Selectors.KINO_HD_IFRAME)?.attr(Selectors.SRC_ATTR).orEmpty()
    }
    return url
}

fun getHDUrlData(
    page: Element
): CinemaUrlData {
    val scripts =
        page.select(Selectors.KINO_HD_SCRIPT + "," + Selectors.KINO_HD_SCRIPT2).map { it.data() }
    val script = scripts
        .map { it.inlineText() }
        .filter { it.isNotBlank() }
        .find {
            val dash = findByGroup(it, Selectors.DASH_REGEXP.toRegex(), 1).orEmpty()
            val hls = findByGroup(it, Selectors.HLS_REGEXP.toRegex(), 1).orEmpty()
            dash.isNotEmpty() || hls.isNotEmpty()
        }.orEmpty()
    val dash = findByGroup(script, Selectors.DASH_REGEXP.toRegex(), 1).orEmpty()
    val hls = findByGroup(script, Selectors.HLS_REGEXP.toRegex(), 1).orEmpty()
    val hdUrls = listOf(dash, hls).filter { it.isNotBlank() }
    return CinemaUrlData(
        hdUrl = AnwapUrl(urls = hdUrls)
    )
}

fun getUrlsData(
    scriptData: String,
    regex: Regex,
    simpleRegex: Regex,
    require: Boolean,
    fileEnds: String = "(.mp4|.m3u8|.mpd)"
): AnwapUrl {
    var data = findByGroup(scriptData.inlineText(), regex, 1).orEmpty()
    if (data.isBlank()) {
        data = findByGroup(scriptData.inlineText(), simpleRegex, 1).orEmpty()
    }
    if (data.isBlank() && scriptData.isNotBlank()) {
        val jsonObject = JSONObject(scriptData.substringAfter("PlayerjsPoster").substring(1).substringBefore("})")+"}")
        data = jsonObject["file"].toString()
    }
    if (data.isNotBlank()) {
        val encryptedData = data.cleanAnwapEncryptedData()
        val decodedData = encryptedData.getDecodedData()
        var urls = if (decodedData.contains("m3u8") || decodedData.contains("mp4") || decodedData.contains("mpd")) {
            getUrlsFromFile(decodedData, fileEnds)
        } else {
            val urlData = getUrlData(decodedData, require, data)
            val file = urlData.file.orEmpty()
            getUrlsFromFile(file, fileEnds)
        }
        if (require) {
            urls = filterTrailer(urls)
        }
        val nonASCII = urls.find {
            "[^\\u0000-\\u007F]+".toRegex() in it
        }
        val incorrectUrls = urls.filter {
            !it.endsWith("m3u8") && !it.endsWith("mp4") && !it.endsWith("mpd")
        }
        if (incorrectUrls.isNotEmpty()) {
            println("fail decrypted data -> $data")
            error("urls has fail format '$urls'")
        }
        if (nonASCII != null) {
            println("fail decrypted data -> $data")
            error("urls has nonASCII symbols '$urls'")
        }
        if (urls.isEmpty()) {
            val s = StringBuilder().apply {
                append("data:$data")
                append("\ndecodedData:$decodedData\n")
            }.toString()
            println("empty urls by data -> $s")
        }
        return AnwapUrl(urls = urls)
    }
    return AnwapUrl()
}

private fun getUrlData(decodedData: String, require: Boolean, data: String): AnwapUrl {
    val urlData = try {
        Json.decodeFromString(decodedData)
    } catch (e: Exception) {
        if (require) {
            throw ParsingRetryException(
                StringBuilder().apply {
                    append("data:$data")
                    append("\ndecodedData:$decodedData\n")
                    append("error:${e.stackTraceToString()}")
                }.toString()
            )
        } else {
            println(
                StringBuilder().apply {
                    append("error:${e.stackTraceToString()}")
                }.toString()
            )
            AnwapUrl()
        }
    }
    return urlData
}

fun filterTrailer(urls: List<String>): List<String> {
    return urls.filter { !it.contains("http(s?)://tr.anwap.be/".toRegex()) }
}

fun getUrlsFromFile(file: String, fileEnds: String = "(.mp4|.m3u8|.mpd)"): List<String> =
    file.split("\\sor\\s".toRegex())
        .map { it.substringBefore("\",\"") }
        .filter { "^http(s?).*?$fileEnds$".toRegex().matches(it) }

fun getSeasonsLinks(page: Element): List<String> {
    return page.select(Selectors.SEASONS).map { it.attr(Selectors.SEASONS_ATTR) }
}

fun getAllEpisodes(page: Element): Int {
    val elements = page.select(Selectors.SEASONS)
    val list = elements.map {
        findByGroup(
            input = it.text().inlineText(),
            regex = Selectors.EPISODES_REGEXP.toRegex(),
            group = 1
        )
    }
    return list.sumOf { it!!.toInt() }
}

fun getSumEpisodesBySeason(page: Element, season: Int): Int {
    val elements = page.select(Selectors.SEASONS)
    val list = elements.map {
        findByGroup(
            input = it.text().inlineText(),
            regex = Selectors.EPISODES_REGEXP.toRegex(),
            group = 1
        )
    }
    return list[season]!!.toInt()
}

fun getEpisodesLinks(page: Element): List<String> {
    return page.select(Selectors.EPISODES).map { it.attr(Selectors.HREF_ATTR) }
}

fun getEpisodesNames(page: Element): List<String> {
    return page.select(Selectors.EPISODES).map { it.text() }
}

fun getAllDownloadLinks(page: Element): List<String> {
    return page.select(Selectors.DOWNLOAD_LINK).map { it.attr(Selectors.HREF_ATTR) }
}

fun Element.getAllCinemaLinks(): List<String> =
    select(Selectors.ALL_CINEMA_LINKS).map { it.attr(Selectors.HREF_ATTR) }

fun getSerialIframeLink(page: Element): String {
    var serialIframeLink =
        page.selectFirst(Selectors.SERIAL_IFRAME)?.attr(Selectors.IFRAME_ATTR).orEmpty()
    if (serialIframeLink.isBlank()) {
        val scriptData = page.selectFirst(Selectors.SERIAL_IFRAME_SCRIPT)?.data().orEmpty()
        serialIframeLink =
            findByGroup(scriptData, Selectors.IFRAME_SRC_REGEXP.toRegex(), 1).orEmpty()
    }
    return serialIframeLink
}

fun getSeasons(page: Element): List<SerialSeason> {
    val seasonsData = page.selectFirst(Selectors.SERIAL_IFRAME_SCRIPT_SEASONS)?.data().orEmpty()
    if (seasonsData.isEmpty()) {
        return emptyList()
    }
    val data = findByGroup(
        input = seasonsData.inlineText(),
        regex = Selectors.SERIAL_EPISODES_REGEXP.toRegex(),
        group = 1
    ).orEmpty()
    val serialText = try {
        data.replace("\\$\\{(\\w+)}".toRegex(), "$1")
    } catch (e: Exception) {
        Timber.e(e)
        data
    }
    val seasons = try {
        serialText.fromJsonToList<SerialSeason>(
            GsonBuilder()
                .setLenient()
                .registerTypeAdapter(
                    ArrayList::class.java,
                    JsonDeserializer { json, _, _ -> readSeasons(json) }
                )
                .create())
    } catch (e: Exception) {
        println("data:$data")
        println("serialText:$serialText")
        e.printStackTrace()
        val s = StringBuilder().apply {
            append("data:$data")
            append("\nserialText:$serialText\n")
            append("error:${e.stackTraceToString()}")
        }.toString()
        throw Exception(s)
    }
    seasons.forEach { season ->
        season.apply {
            this.episodes = episodes.map { episode ->
                episode.copy(
                    dash = episode.dash.replace("\\s+".toRegex(), ""),
                    hls = episode.hls.replace("\\s+".toRegex(), ""),
                    poster = episode.poster.replace("\\s+".toRegex(), ""),
                )
            }
        }
    }
    return seasons.sortedBy { it.id }
}

fun getProxies(page: Element?, selector: String, regex: Regex): List<String> {
    val text = page?.selectFirst(selector)?.text().orEmpty()
    val stringList = findAllByGroup(text, regex, 1)
    return stringList.map { it.replace(" ", ":") }
}

fun getProxies(text: String, regex: Regex): List<String> {
    val stringList = findAllByGroup(text, regex, 1)
    return stringList.map { it.replace(" ", ":") }
}
