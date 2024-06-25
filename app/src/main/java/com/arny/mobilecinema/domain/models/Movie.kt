package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import com.arny.mobilecinema.data.db.models.IMovieUpdate
import kotlinx.parcelize.Parcelize

@Parcelize
data class Movie(
    val dbId: Long = 0,
    val movieId: Int = 0,
    val title: String = "",
    val origTitle: String = "",
    val type: MovieType = MovieType.NO_TYPE,
    override val pageUrl: String = "",
    val img: String = "",
    val info: MovieInfo = MovieInfo(),
    val seasons: List<SerialSeason> = emptyList(),
    val cinemaUrlData: CinemaUrlData? = null,
    val cached: Boolean = false,
    val seasonPosition: Int? = null,
    val episodePosition: Int? = null,
    val time: Long? = null,
    val customData: String? = null,
) : Parcelable, IMovieUpdate
