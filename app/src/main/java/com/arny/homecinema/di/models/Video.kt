package com.arny.homecinema.di.models

import android.os.Parcel
import android.os.Parcelable

data class Video(
    val id: Int? = null,
    var videoUrl: String? = null,
    var currentPosition: Long = 0,
    var playWhenReady: Boolean = false,
    val hlsList: HashMap<String, String>? = null,
    val selectedHls: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        videoUrl = parcel.readString(),
        currentPosition = parcel.readLong(),
        playWhenReady = parcel.readByte() != 0.toByte(),
        hlsList = parcel.readSerializable() as? (HashMap<String, String>),
        selectedHls = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id ?: 0)
        parcel.writeString(videoUrl)
        parcel.writeLong(currentPosition)
        parcel.writeByte(if (playWhenReady) 1 else 0)
        parcel.writeSerializable(hlsList)
        parcel.writeString(selectedHls)
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