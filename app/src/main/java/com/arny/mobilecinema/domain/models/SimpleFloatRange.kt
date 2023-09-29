package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleFloatRange(
    val from: Float,
    val to: Float,
) : Parcelable
