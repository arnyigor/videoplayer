package com.arny.mobilecinema.di.models

import android.os.Parcelable
import com.arny.mobilecinema.domain.models.SerialSeason
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerialData(val seasons: List<SerialSeason>? = null) : Parcelable