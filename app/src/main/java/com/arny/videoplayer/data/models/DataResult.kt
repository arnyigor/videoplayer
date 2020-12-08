package com.arny.videoplayer.data.models

sealed class DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>()
    data class Error<out T>(val throwable: Throwable) : DataResult<T>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$throwable]"
        }
    }
}

fun <T> T.toResult(): DataResult<T> {
    return try {
        DataResult.Success(this)
    } catch (e: Exception) {
        DataResult.Error(e)
    }
}

fun <T> Throwable.toResult() = DataResult.Error<T>(this)