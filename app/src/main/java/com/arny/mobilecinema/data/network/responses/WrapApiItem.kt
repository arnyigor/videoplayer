package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class WrapApiItem(
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("img") val img: WrapApiImg? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("title") val title: String? = null
)