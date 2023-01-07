package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class WrapGist(
    @SerializedName("data")
    val wrapGistData: WrapGistData? = null,
    @SerializedName("success")
    val success: Boolean = false
)