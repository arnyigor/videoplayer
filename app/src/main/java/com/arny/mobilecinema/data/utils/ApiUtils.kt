package com.arny.mobilecinema.data.utils

import com.yamusic.get.utils.strings.ParametricString
import retrofit2.HttpException
import javax.net.ssl.SSLHandshakeException

fun Throwable.getFullError() = ParametricString(getFullError(this))

fun getFullError(throwable: Throwable): String {
    throwable.printStackTrace()
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
                    404 -> error = "Страница не найдена"
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
    return error
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
