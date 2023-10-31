package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleIntRange(
    val from: Int = 0,
    val to: Int = 0,
) : Parcelable

fun SimpleIntRange?.isNotEmpty(): Boolean = this != null && from != 0 && to != 0