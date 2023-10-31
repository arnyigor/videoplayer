package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MoviesData(
    val movies: List<Movie> = emptyList(),
) : Parcelable