package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CinemaUrlData(
    val hdUrl: AnwapUrl? = null,
    val cinemaUrl: AnwapUrl? = null,
) : Parcelable
