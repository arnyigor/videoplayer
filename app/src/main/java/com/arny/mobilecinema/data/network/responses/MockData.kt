package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class MockData(
    @SerializedName("episodes")
    val episodes: List<String?> = emptyList(),
    @SerializedName("img")
    val img: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("type")
    val type: String? = null
)