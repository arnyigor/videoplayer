package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.utils.fromJson

fun getHlsListMap(hlslist:String): HashMap<String, String> {
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