package com.arny.mobilecinema.di.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialSeason(
    val id: Int? = null,
    val episodes: List<SerialEpisode>? = null
) : Parcelable