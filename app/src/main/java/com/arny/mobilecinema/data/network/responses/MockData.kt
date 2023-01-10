package com.arny.mobilecinema.data.network.responses

import com.arny.mobilecinema.di.models.SerialSeason
import com.google.gson.annotations.SerializedName

data class MockData(
    @SerializedName("seasons") val seasons: List<SerialSeason?>?=null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null
)