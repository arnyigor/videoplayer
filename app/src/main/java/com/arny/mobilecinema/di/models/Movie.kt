package com.arny.mobilecinema.di.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Movie constructor(
    val uuid: String = "",
    val title: String = "",
    val type: MovieType = MovieType.NO_TYPE,
    val detailUrl: String? = null,
    val img: String? = null,
    val video: Video? = null,
    val serialData: SerialData? = null,
    val currentSeasonPosition: Int = 0,
    val currentEpisodePosition: Int = 0,
    val selectedQuality: String? = null,
    val baseUrl: String = "",
    val info: String = "",
    val fullDescr: String = "",
    val trailer: String = ""
) : Parcelable
