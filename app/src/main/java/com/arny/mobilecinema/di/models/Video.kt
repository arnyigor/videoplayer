package com.arny.mobilecinema.di.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Video(
    val id: Int? = null,
    val title: String? = null,
    val episode: Int? = null,
    val season: Int? = null,
    var videoUrl: String? = null,
    var currentPosition: Long = 0,
    var playWhenReady: Boolean = false,
    val hlsList: HashMap<String, String>? = null,
    val selectedHls: String? = null,
    val type: MovieType? = null
) : Parcelable