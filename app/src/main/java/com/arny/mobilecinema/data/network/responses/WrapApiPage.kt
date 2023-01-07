package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class WrapApiPage(
    @SerializedName("item")
    val item: List<WrapApiItem>? = listOf(),
    @SerializedName("pages")
    val pages: String? = ""
)