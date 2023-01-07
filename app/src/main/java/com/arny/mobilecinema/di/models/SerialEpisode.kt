package com.arny.mobilecinema.di.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialEpisode(
	val id: Int? = null,
	val title: String? = null,
	val hlsList: HashMap<String, String>? = null,
	val selectedHls: String? = null
) : Parcelable
