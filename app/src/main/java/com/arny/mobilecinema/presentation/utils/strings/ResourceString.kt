package com.arny.mobilecinema.presentation.utils.strings

import android.content.Context
import androidx.annotation.StringRes
import com.arny.mobilecinema.presentation.utils.IWrappedString

class ResourceString(@StringRes val resString: Int, private vararg val params: Any?) :
    IWrappedString {

    override fun toString(context: Context): String? {
        return context.getString(resString, *params)
    }
}