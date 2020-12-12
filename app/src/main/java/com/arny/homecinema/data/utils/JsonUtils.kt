package com.arny.homecinema.data.utils

import com.google.gson.Gson

fun Any?.toJson(): String? {
    return if (this != null) Gson().toJson(this) else null
}

fun <T> Any?.fromJson(cls: Class<T>): T? {
    return Gson().fromJson(this.toString(), cls)
}