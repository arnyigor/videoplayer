package com.arny.mobilecinema.data.models

import com.google.gson.annotations.SerializedName

data class SeasonItem(
    @SerializedName("season")
    val season: Int,
    @SerializedName("episodes")
    val episodes: List<EpisodesItem?>
)

data class EpisodesItem(
    @SerializedName("hlsList")
    val hlsList: HashMap<String, String>,
    @SerializedName("episode")
    val episode: String,
    @SerializedName("title")
    val title: String
)
