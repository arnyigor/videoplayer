package com.arny.homecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class Video(
    val title: String,
    val infoUrl: String? = null,
    val img: String? = null,
    var videoUrl: String? = null,
    var currentPosition: Long = 0,
    var playWhenReady: Boolean = false,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(infoUrl)
        parcel.writeString(img)
        parcel.writeString(videoUrl)
        parcel.writeLong(currentPosition)
        parcel.writeByte(if (playWhenReady) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Video> {
        override fun createFromParcel(parcel: Parcel): Video {
            return Video(parcel)
        }

        override fun newArray(size: Int): Array<Video?> {
            return arrayOfNulls(size)
        }
    }
}
