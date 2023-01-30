package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnwapMovie constructor(
    var dbId: Int = 0,
    var movieId: Int = 0,
    var title: String = "",
    var origTitle: String = "",
    var type: MovieType = MovieType.NO_TYPE,
    var pageUrl: String = "",
    var img: String = "",
    var info: MovieInfo = MovieInfo(),
    var seasons: List<SerialSeason> = emptyList(),
    var cinemaUrlData: CinemaUrlData? = null,
):Parcelable
