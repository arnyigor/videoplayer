package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class WrapApiData(
    @SerializedName("items")
    val items: List<WrapApiPage>? = listOf(),
    @SerializedName("pages")
    val pages: String? = ""
)