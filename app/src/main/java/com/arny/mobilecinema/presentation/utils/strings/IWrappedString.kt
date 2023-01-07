package com.arny.mobilecinema.presentation.utils.strings

import android.content.Context

interface IWrappedString {
    fun toString(context: Context): String?
}