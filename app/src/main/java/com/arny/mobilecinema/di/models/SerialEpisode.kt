package com.arny.mobilecinema.di.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	@SerializedName("id")  val id: Int = 0,
	@SerializedName("title") val title: String? = null,
	@SerializedName("hls") val hls: HashMap<String, String>? = null,
) : Parcelable
