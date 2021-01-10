package com.arny.mobilecinema.data.utils

import com.arny.mobilecinema.data.models.DataResult
import retrofit2.HttpException
import javax.net.ssl.SSLHandshakeException

fun <T> getFullError(throwable: Throwable): DataResult<T> {
    var error: String
    val code: Int
    try {
        when (throwable) {
            is HttpException -> {
                code = throwable.code()
                error = throwable.response()?.errorBody()?.string().toString()
                when (code) {
                    500 -> error = "Внутренняя ошибка сервера"
                    504 -> error = "Время ожидания истекло, повторите запрос позже"
                    503 -> error = "Сервис временно недоступен, повторите запрос позже"
                    403 -> error = "Сервис заблокирован"
                }
            }
            is SSLHandshakeException -> {
                error = "Ошибка сертификата сервера"
            }
            else -> {
                error = getMessage(throwable)
            }
        }
    } catch (e: Exception) {
        error = getMessage(throwable)
    }
    return DataResult.Error(Throwable(error))
}

private fun getMessage(throwable: Throwable): String {
    val error: String
    val message = throwable.message ?: "Ошибка запроса"
    error = when {
        message.contains("Unable to resolve host", true) ||
                message.contains(
                    "failed to connect",
                    true
                ) -> "Ошибка соединения, адрес недоступен"
        message.contains("timeout", true) -> "Время запроса истекло, попробуйте еще раз"
        else -> message
    }
    return error
}