package com.arny.homecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class Movie constructor(
    val uuid: String,
    val title: String,
    val type: MovieType,
    val detailUrl: String? = null,
    val img: String? = null,
    val video: Video? = null,
    val serialData: SerialData? = null,
    val currentSeasonPosition: Int = 0,
    val currentEpisodePosition: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "-",
        MovieType.fromValue(parcel.readInt()),
        parcel.readString(),
        parcel.readString(),
        parcel.readParcelable(Video::class.java.classLoader),
        parcel.readParcelable(SerialData::class.java.classLoader),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uuid)
        parcel.writeString(title)
        parcel.writeInt(type.type)
        parcel.writeString(detailUrl)
        parcel.writeString(img)
        parcel.writeParcelable(video, flags)
        parcel.writeParcelable(serialData, flags)
        parcel.writeInt(currentSeasonPosition)
        parcel.writeInt(currentEpisodePosition)
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
