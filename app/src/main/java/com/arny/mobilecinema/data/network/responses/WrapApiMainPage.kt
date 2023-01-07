package com.arny.mobilecinema.data.network.responses


import com.google.gson.annotations.SerializedName

data class WrapApiMainPage(
    @SerializedName("data")
    val wrapApiData: WrapApiData? = WrapApiData(),
    @SerializedName("success")
    val success: Boolean? = false
)