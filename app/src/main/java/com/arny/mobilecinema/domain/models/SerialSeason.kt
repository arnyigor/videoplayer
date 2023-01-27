package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialSeason(
    var id: Int? = null,
    var episodes: List<SerialEpisode> = emptyList()
) : Parcelable