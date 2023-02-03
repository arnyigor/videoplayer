package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CinemaUrlData(
    val hdUrl: UrlData? = null,
    val cinemaUrl: UrlData? = null,
    val trailerUrl: UrlData? = null
):Parcelable
