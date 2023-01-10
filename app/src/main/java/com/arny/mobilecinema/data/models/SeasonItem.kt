package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.di.models.SerialEpisode
import com.google.gson.annotations.SerializedName

data class SeasonItem(
    @SerializedName("season") val season: Int,
    @SerializedName("episodes") val episodes: List<SerialEpisode?>
)