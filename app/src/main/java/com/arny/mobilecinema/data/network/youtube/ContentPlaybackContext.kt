package com.arny.mobilecinema.data.network.youtube

import com.google.gson.annotations.SerializedName

data class ContentPlaybackContext(
    @SerializedName("autoCaptionsDefaultOn")
    val autoCaptionsDefaultOn: Boolean = false,
    @SerializedName("autonavState")
    val autonavState: String = "STATE_NONE",
    @SerializedName("html5Preference")
    val html5Preference: String? = "HTML5_PREF_WANTS",
    @SerializedName("lactMilliseconds")
    val lactMilliseconds: String = "-1",
    @SerializedName("splay")
    val splay: Boolean = false,
    @SerializedName("vis")
    val vis: Int = 0
)