package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	val id: Int = 0,
	val episode: String = "",
	val title: String = "",
	val hls: String = "",
	val dash: String = "",
	val poster: String = "",
) : Parcelable
