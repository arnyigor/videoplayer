package com.arny.mobilecinema.di.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	@SerializedName("id")  val id: Int? = null,
	@SerializedName("title") val title: String? = null,
	@SerializedName("hlsList") val hlsList: HashMap<String, String>? = null,
) : Parcelable
