package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Movie(
    val dbId: Long = 0,
    val movieId: Int = 0,
    val title: String = "",
    val origTitle: String = "",
    val type: MovieType = MovieType.NO_TYPE,
    val pageUrl: String = "",
    val img: String = "",
    val info: MovieInfo = MovieInfo(),
    val seasons: List<SerialSeason> = emptyList(),
    val cinemaUrlData: CinemaUrlData? = null,
    val cached: Boolean = false
):Parcelable
