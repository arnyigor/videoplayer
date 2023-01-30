package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Data constructor(
    val movies: List<AnwapMovie> = emptyList(),
) : Parcelable