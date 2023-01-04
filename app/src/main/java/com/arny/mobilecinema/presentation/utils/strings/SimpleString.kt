package com.arny.mobilecinema.presentation.utils.strings

import android.content.Context
import com.arny.mobilecinema.presentation.utils.IWrappedString

class SimpleString(val string: String?) : IWrappedString {
    override fun toString(context: Context): String? = string
}