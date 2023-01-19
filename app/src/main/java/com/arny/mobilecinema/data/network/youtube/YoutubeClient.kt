package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class YoutubeClient(
    @SerializedName("clientFormFactor") val clientFormFactor: String = "UNKNOWN_FORM_FACTOR",
    @SerializedName("clientName") val clientName: String = "WEB",
    @SerializedName("clientScreen") val clientScreen: String = "WATCH",
    @SerializedName("clientVersion") val clientVersion: String = "2.20210721.00.00",
    @SerializedName("hl") val hl: String = "en",
    @SerializedName("mainAppWebInfo") val mainAppWebInfo: MainAppWebInfo = MainAppWebInfo()
)