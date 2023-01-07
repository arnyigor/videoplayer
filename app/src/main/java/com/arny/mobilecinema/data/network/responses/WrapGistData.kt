package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class WrapGistData(
    @SerializedName("link") val link: String? = null
)