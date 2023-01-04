package com.yamusic.get.utils.strings

import android.content.Context
import com.arny.mobilecinema.presentation.utils.IWrappedString

class ParametricString(private val format: String, private vararg val params: Any?) :
    IWrappedString {
    override fun toString(context: Context): String = try {
        String.format(format, *params)
    } catch (e: Exception) {
       ""
    }
}
