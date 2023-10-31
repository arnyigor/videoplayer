package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleFloatRange(
    val from: Float = 0.0f,
    val to: Float = 0.0f,
) : Parcelable

fun SimpleFloatRange?.isNotEmpty(): Boolean = this != null && from != 0.0f && to != 0.0f