package com.arny.mobilecinema.data.models

sealed class DataResultWithProgress<out T : Any> {
    data class Success<out T : Any>(val result: T) : DataResultWithProgress<T>()
    data class Error(val throwable: Throwable) : DataResultWithProgress<Nothing>()
    data class Progress<out T : Any>(val result: T) : DataResultWithProgress<T>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$result]"
            is Error -> "Error[exception=$throwable]"
            is Progress -> "Progress[data=$result]"
        }
    }
}
