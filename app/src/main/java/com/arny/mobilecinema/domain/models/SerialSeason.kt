package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialSeason(
   @SerializedName("season") val id: Int? = null,
   @SerializedName("episodes") val episodes: List<SerialEpisode> = emptyList()
) : Parcelable