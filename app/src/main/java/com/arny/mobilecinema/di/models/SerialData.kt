package com.arny.mobilecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class SerialData(
	val seasons: List<SerialSeason>? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.createTypedArrayList(SerialSeason))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(seasons)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SerialData> {
        override fun createFromParcel(parcel: Parcel): SerialData {
            return SerialData(parcel)
        }

        override fun newArray(size: Int): Array<SerialData?> {
            return arrayOfNulls(size)
        }
    }
}
