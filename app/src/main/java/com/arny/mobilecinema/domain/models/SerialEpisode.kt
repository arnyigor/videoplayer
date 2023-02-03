package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	@SerializedName("id") val id: Int = 0,
	@SerializedName("episode") val episode: String = "",
	@SerializedName("title") val title: String = "",
	@SerializedName("hls") val hls: String = "",
	@SerializedName("dash") val dash: String = "",
	@SerializedName("poster") val poster: String = "",
) : Parcelable
