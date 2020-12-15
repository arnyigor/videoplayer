package com.arny.homecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class SerialSeason(
	val id: Int? = null,
	val episodes: List<SerialEpisode>? = null
):Parcelable {
	constructor(parcel: Parcel) : this(
		parcel.readValue(Int::class.java.classLoader) as? Int,
		parcel.createTypedArrayList(SerialEpisode)
	)

	override fun writeToParcel(parcel: Parcel, flags: Int) {
		parcel.writeValue(id)
		parcel.writeTypedList(episodes)
	}

	override fun describeContents(): Int {
		return 0
	}

	companion object CREATOR : Parcelable.Creator<SerialSeason> {
		override fun createFromParcel(parcel: Parcel): SerialSeason {
			return SerialSeason(parcel)
		}

		override fun newArray(size: Int): Array<SerialSeason?> {
			return arrayOfNulls(size)
		}
	}
}
