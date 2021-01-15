package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.models.SeasonItem
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.di.models.SerialData
import com.arny.mobilecinema.di.models.SerialEpisode
import com.arny.mobilecinema.di.models.SerialSeason
import java.util.*

fun String.toHlsListMap(): HashMap<String, String> {
    val qualityMap = hashMapOf<String, String>()
    this.fromJson(String::class.java) { jsonElement ->
        for ((key, value) in jsonElement.asJsonObject.entrySet()) {
            qualityMap[key] = value.asString
        }
        qualityMap
    }
    return qualityMap
}

fun regexBetwenTwoString(start: String, end: String): Regex {
    return "(?<=$start)([\\s\\S]+?)(?=$end)".toRegex()
}

fun correctTitle(title: String?): String {
    var result = title
    listOf(
        "^(.*)\\s(\\d+-?\\d+\\s?сезон)".toRegex(RegexOption.IGNORE_CASE),
        "^(.*)[\\s]?(\\d+[\\s]?сезон)".toRegex(RegexOption.IGNORE_CASE),
        "^(\\D+)\\s?(\\d+,\\s)".toRegex(RegexOption.IGNORE_CASE),
        "^(\\D+)\\s?(\\d+,?\\s)серия".toRegex(RegexOption.IGNORE_CASE),
        "(.*)\\sсмотреть фильм онлайн".toRegex(RegexOption.IGNORE_CASE),
        "^фильм(.*)\\sв hd \\d+ качестве смотреть онлайн".toRegex(RegexOption.IGNORE_CASE),
        "^(.*)\\sв hd \\d+ качестве смотреть онлайн".toRegex(RegexOption.IGNORE_CASE),
        "^(.*)\\sсмотреть онлайн".toRegex(RegexOption.IGNORE_CASE),
        "^фильм\\s*(.*)\\sв hd".toRegex(RegexOption.IGNORE_CASE),
        "^(.*)\\sв hd".toRegex(RegexOption.IGNORE_CASE),
    ).asSequence()
        .forEach {
            val finded = it.find(title ?: "")?.groupValues?.getOrNull(1)?.trim()
            if (!finded.isNullOrBlank()) {
                result = finded
                return result ?: ""
            }
        }
    return result ?: ""
}

fun String.parseSerialData(): SerialData {
    val seasons = mutableListOf<SerialSeason>()
    this.fromJson(ArrayList::class.java) { jsonElement ->
        for (element in jsonElement.asJsonArray) {
            element.fromJson(SeasonItem::class.java)?.let { movie ->
                seasons.add(movie.fillEposides())
            }
        }
    }
    seasons.sortBy { it.id }
    return SerialData(seasons)
}

fun String.clearSymbols(clear: Boolean = true): String {
    return if (clear) {
        this.replace("\n", "")
            .replace("\t", "")
            .replace("\\s+".toRegex(), " ")
    } else {
        this
    }
}

fun List<String>.toMap(): HashMap<String, String> {
    val videoQualityMap = hashMapOf<String, String>()
    for (hls in this) {
        val quality = hls.substringBefore(":")
        val link = hls.substringAfter(":")
        if (quality.isNotBlank() && link.isNotBlank()) {
            videoQualityMap[quality] = link
        }
    }
    return videoQualityMap
}

fun String.getParsingString(start: String, end: String) = "$start$this$end"

fun String.substringAfterBefore(after: String, before: String) =
    this.substringAfter(after).substringBefore(before)

private fun SeasonItem.fillEposides(): SerialSeason {
    val episodes = mutableListOf<SerialEpisode>()
    for (episodesItem in this.episodes) {
        val serialEpisode = SerialEpisode(
            id = episodesItem.episode.toIntOrNull() ?: 0,
            title = episodesItem.title,
            hlsList = episodesItem.hlsList
        )
        episodes.add(serialEpisode)
    }
    episodes.sortBy { it.id }
    return SerialSeason(this.season, episodes)
}