package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.di.models.SerialData
import com.arny.mobilecinema.di.models.SerialEpisode
import com.arny.mobilecinema.di.models.SerialSeason

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
    result = prefilterTitle(title, result)
    listOf(
        "смотреть\\s*[фильм]*\\s*онлайн".toRegex(RegexOption.IGNORE_CASE),
        "(смотреть фильм|в хорошем качестве|в HD качестве|в hd \\d+ качестве|hd бесплатно)".toRegex(
            RegexOption.IGNORE_CASE
        ),
        "^фильм\\s*".toRegex(RegexOption.IGNORE_CASE),
        "\\(фильм\\s*\\d+\\)".toRegex(RegexOption.IGNORE_CASE),
        "hd бесплатно".toRegex(RegexOption.IGNORE_CASE),
        "в хорошем качестве".toRegex(RegexOption.IGNORE_CASE),
        "в hd \\d+ качестве".toRegex(RegexOption.IGNORE_CASE),
    ).asSequence().forEach {
        result = result?.replace(it, "")
    }
    return result?.trimIndent() ?: ""
}

private fun prefilterTitle(title: String?, result: String?): String? {
    var result1 = result
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
                result1 = finded
                return result1 ?: ""
            }
        }
    return result1
}

fun String.parseSerialData(): SerialData {
    val seasons = mutableListOf<SerialSeason>()
    this.fromJson(ArrayList::class.java) { jsonElement ->
        for (element in jsonElement.asJsonArray) {
            element.fromJson(SerialSeason::class.java)?.let { movie ->
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

private fun SerialSeason.fillEposides(): SerialSeason {
    val episodes = mutableListOf<SerialEpisode>()
    for (episodesItem in this.episodes!!) {
        if (episodesItem!=null) {
            val serialEpisode = SerialEpisode(
                id = episodesItem.id ?: 0,
                title = episodesItem.title,
                hlsList = episodesItem.hlsList
            )
            episodes.add(serialEpisode)
        }
    }
    episodes.sortBy { it.id }
    return SerialSeason(this.id, episodes)
}