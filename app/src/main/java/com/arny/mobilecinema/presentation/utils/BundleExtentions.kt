package com.arny.mobilecinema.presentation.utils

import android.content.Intent
import android.os.Bundle

fun Bundle?.dump(): String? {
    val stringBuilder = StringBuilder()
    if (this != null) {
        for (key in this.keySet()) {
            val value = this[key]
            if (value != null) {
                stringBuilder.append(
                    String.format(
                        " class(%s) %s->%s",
                        value.javaClass.simpleName,
                        key,
                        value.toString()
                    )
                )
            }
        }
        return stringBuilder.toString()
    }
    return null
}

fun Intent?.dump(): String? = this?.extras?.dump()