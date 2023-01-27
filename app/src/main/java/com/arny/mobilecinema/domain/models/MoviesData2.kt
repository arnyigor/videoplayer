package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MoviesData2 constructor(
    val movies: List<AnwapMovie> = emptyList(),
) : Parcelable