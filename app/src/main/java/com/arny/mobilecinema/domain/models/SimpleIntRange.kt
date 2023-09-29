package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleIntRange(
    val from: Int,
    val to: Int,
) : Parcelable
