package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MovieInfo(
    val year: Int = 0,
    val quality: String = "",
    val translate: String = "",
    val durationSec: Int = 0,
    val age: Int = -1,
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val likes: Int = 0,
    val dislikes: Int = 0,
    val ratingImdb: Double = -1.0,
    val ratingKP: Double = -1.0,
    val directors: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val description: String = "",
    val updated: Long = 0L,
    val origTitle: String = "",
) :Parcelable