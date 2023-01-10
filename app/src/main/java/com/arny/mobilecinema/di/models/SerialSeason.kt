package com.arny.mobilecinema.di.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialSeason(
    @SerializedName("season") val seasonId: Int? = null,
    @SerializedName("episodes") val episodes: List<SerialEpisode>? = null
) : Parcelable