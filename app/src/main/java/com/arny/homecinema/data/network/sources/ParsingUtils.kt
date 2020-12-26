package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.utils.fromJson

fun getHlsListMap(hlslist: String): HashMap<String, String> {
    val qualityMap = hashMapOf<String, String>()
    hlslist.fromJson(String::class.java) { jsonElement ->
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
        "^(.*)\\s(\\d+-?\\d+\\s?сезон)".toRegex(),
        "^(.*)[\\s]?(\\d+[\\s]?сезон)".toRegex(),
        "^(\\D+)\\s?(\\d+,\\s)".toRegex(),
        "^(\\D+)\\s?(\\d+,?\\s)серия".toRegex(),
        "^(.*)\\sсмотреть онлайн".toRegex(),
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