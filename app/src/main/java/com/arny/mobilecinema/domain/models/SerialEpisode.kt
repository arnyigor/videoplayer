package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	var id: Int = 0,
	var episode: String = "",
	var title: String = "",
	var hls: String = "",
	var dash: String = "",
	var poster: String = "",
) : Parcelable
