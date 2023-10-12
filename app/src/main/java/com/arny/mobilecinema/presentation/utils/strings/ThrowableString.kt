package com.arny.mobilecinema.presentation.utils.strings

import android.content.Context
import com.arny.mobilecinema.data.models.DataThrowable

class ThrowableString(val throwable: Throwable?) : IWrappedString {
    override fun toString(context: Context): String {
        return when (throwable) {
            is DataThrowable -> context.getString(throwable.errorRes)
            else -> throwable?.message.orEmpty()
        }
    }
}