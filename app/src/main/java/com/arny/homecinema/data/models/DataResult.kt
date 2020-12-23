package com.arny.homecinema.data.models

import com.arny.homecinema.data.utils.getFullError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

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
        getFullError(e)
    }
}

fun <T> Flow<T>.catchResult(): Flow<T> {
    return this.catch {
        getFullError<T>(it)
    }
}