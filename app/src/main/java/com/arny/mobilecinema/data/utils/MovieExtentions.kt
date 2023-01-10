package com.arny.mobilecinema.data.utils

import com.arny.mobilecinema.di.models.MovieType

fun String?.toMovieType(): MovieType = when (this) {
    "serial" -> MovieType.SERIAL
    "cinema" -> MovieType.CINEMA
    else -> MovieType.CINEMA
}