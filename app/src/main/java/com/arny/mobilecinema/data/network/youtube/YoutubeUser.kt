package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class YoutubeUser(
    @SerializedName("lockedSafetyMode") val lockedSafetyMode: Boolean  = false
)