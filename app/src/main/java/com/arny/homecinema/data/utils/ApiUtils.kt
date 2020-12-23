package com.arny.homecinema.data.utils

import com.arny.homecinema.data.models.DataResult
import retrofit2.HttpException

fun <T> getFullError(throwable: Throwable): DataResult<T> {
    var error: String
    val code: Int
    try {
        when (throwable) {
            is HttpException -> {
                code = throwable.code()
                error = throwable.response()?.errorBody()?.string().toString()
                when (code) {
                    504 -> error = "Время ожидания истекло, повторите запрос позже"
                    503 -> error = "Сервис временно недоступен, повторите запрос позже"
                    403 -> error = "Сервис заблокирован"
                }
            }
            else -> {
                val message = throwable.message ?: "Ошибка запроса"
                val connectFailed = when {
                    message.contains("Unable to resolve host", true) -> true
                    message.contains("failed to connect", true) -> true
                    else -> false
                }
                error = if (connectFailed) {
                    "Ошибка соединения, адрес недоступен"
                } else {
                    message
                }
            }
        }
    } catch (e: Exception) {
        error = throwable.message ?: "Ошибка запроса"
    }
    return DataResult.Error(Throwable(error))
}