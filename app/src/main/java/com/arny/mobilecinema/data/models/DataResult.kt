package com.arny.mobilecinema.data.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class DataResult<out T : Any> {
    data class Success<out T : Any>(val result: T) : DataResult<T>()
    data class Error(val throwable: Throwable) : DataResult<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$result]"
            is Error -> "Error[exception=$throwable]"
        }
    }
}

fun <T : Any> doAsync(
    request: suspend () -> T?
) = flow<DataResult<T>> {
    request().also { data ->
        emit(DataResult.Success(data!!))
    }
}
    .flowOn(Dispatchers.IO)
    .catch { exception ->
        exception.printStackTrace()
        emit(DataResult.Error(exception))
    }

suspend fun <T : Any> getDataResult(request: suspend () -> T?) = try {
    DataResult.Success(request()!!)
} catch (e: Exception) {
    DataResult.Error(e)
}