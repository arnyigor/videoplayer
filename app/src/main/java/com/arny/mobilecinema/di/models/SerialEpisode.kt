package com.arny.mobilecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class SerialEpisode(
	val id: Int? = null,
	val title: String? = null,
	val hlsList: HashMap<String, String>? = null,
    val selectedHls: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
		id = parcel.readValue(Int::class.java.classLoader) as? Int,
		title = parcel.readString(),
        hlsList = parcel.readSerializable() as? (HashMap<String, String>),
        selectedHls = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(id)
        parcel.writeString(title)
        parcel.writeSerializable(hlsList)
        parcel.writeString(selectedHls)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SerialEpisode> {
        override fun createFromParcel(parcel: Parcel): SerialEpisode {
            return SerialEpisode(parcel)
        }

        override fun newArray(size: Int): Array<SerialEpisode?> {
            return arrayOfNulls(size)
        }
    }
}
