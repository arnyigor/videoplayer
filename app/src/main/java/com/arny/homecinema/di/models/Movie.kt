package com.arny.homecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class Movie(
    val title: String,
    val type: MovieType,
    val infoUrl: String? = null,
    val img: String? = null,
    val video: Video? = null,
    val serialData: SerialData? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "-",
        MovieType.fromValue(parcel.readInt()),
        parcel.readString(),
        parcel.readString(),
        parcel.readParcelable(Video::class.java.classLoader),
        parcel.readParcelable(SerialData::class.java.classLoader)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeInt(type.type)
        parcel.writeString(infoUrl)
        parcel.writeString(img)
        parcel.writeParcelable(video, flags)
        parcel.writeParcelable(serialData, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Movie> {
        override fun createFromParcel(parcel: Parcel): Movie {
            return Movie(parcel)
        }

        override fun newArray(size: Int): Array<Movie?> {
            return arrayOfNulls(size)
        }
    }
}
