package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.domain.models.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

fun readSeasons(json: JsonElement): List<SerialSeason> {
    val seasonsArr = ArrayList<SerialSeason>(100)
    for (element in json.asJsonArray) {
        val jsonObject = element.asJsonObject
        val id = jsonObject["season"].asInt
        val episodesArr = ArrayList<SerialEpisode>(100)
        for (arr in jsonObject["episodes"].asJsonArray) {
            val obj = arr.asJsonObject
            episodesArr.add(
                SerialEpisode(
                    id = obj["id"].asInt,
                    episode = obj["episode"].asString.orEmpty(),
                    title = obj["title"].asString.orEmpty(),
                    hls = obj["hls"].asString.orEmpty(),
                    dash = obj["dash"].asString.orEmpty(),
                    poster = obj["poster"].asString.orEmpty(),
                )
            )
        }
        seasonsArr.add(SerialSeason(id, episodesArr))
    }
    return seasonsArr
}
