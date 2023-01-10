package com.arny.mobilecinema.presentation.utils.strings

import android.content.Context

class ThrowableString(val throwable: Throwable?) : IWrappedString {
    override fun toString(context: Context): String = throwable?.message.orEmpty()
}