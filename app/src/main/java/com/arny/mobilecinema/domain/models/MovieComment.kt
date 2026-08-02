package com.arny.mobilecinema.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MovieComment(
    val id: String = "",
    val author: String = "",
    val authorUrl: String = "",
    val dateText: String = "",
    val text: String = "",
) : Parcelable
