package com.arny.mobilecinema.data.feedback

import android.content.Context
import android.util.Log
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException

class FeedbackDatabaseImpl(
    private val httpClient: HttpClient,
    private val context: Context
) : FeedbackDatabase {

    override suspend fun sendMessage(pageUrl: String, content: String): Boolean {
        return try {
            val requestJson = buildJsonObject {
                put(
                    "appname",
                    buildJsonObject {
                        put(
                            "name",
                            context.applicationInfo.loadLabel(context.packageManager).toString()
                        )
                        put("version", BuildConfig.VERSION_NAME)
                        put("id", BuildConfig.VERSION_CODE.toString())
                        put("packagename", context.packageName)
                    }
                )
                put("content", content.take(10_000))
                put("reference", pageUrl)
            }

            val response = httpClient.post(BuildConfig.FEEDBACK_URL) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append("X-API-Key", BuildConfig.FEEDBACK_API_KEY)
                }
                setBody(requestJson.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.bodyAsText() }.getOrNull()
                Timber.tag("FeedbackDatabaseImpl")
                    .e("sendMessage failed: status=${response.status.value}, body=$errorBody")
            }

            response.status.isSuccess()
        } catch (t: Throwable) {
            // Теперь понятно, что случилось
            Timber.tag("FeedbackDatabaseImpl")
                .e(t, "sendMessage error [${t.toErrorCode()}]: ${t.toReadableError()}")
            false
        }
    }
}




/**
 * Преобразует Throwable в понятное сообщение об ошибке для пользователя или логов
 */
fun Throwable.toReadableError(): String = when (this) {
    // Таймауты
    is HttpRequestTimeoutException,
    is SocketTimeoutException -> "Превышено время ожидания ответа от сервера. Проверьте интернет-соединение."

    // Нет интернета / DNS
    is UnknownHostException -> "Нет подключения к интернету или сервер недоступен."

    // SSL / Сертификаты
    is SSLHandshakeException,
    is CertPathValidatorException -> "Ошибка защищённого соединения. Проверьте дату/время на устройстве."

    // HTTP ошибки клиента (4xx)
    is ClientRequestException -> when (response.status) {
        HttpStatusCode.Unauthorized -> "Ошибка авторизации. Проверьте API-ключ."
        HttpStatusCode.Forbidden -> "Доступ запрещён. Возможно, истёк срок действия ключа."
        HttpStatusCode.NotFound -> "Серверная ошибка: эндпоинт не найден."
        HttpStatusCode.TooManyRequests -> "Слишком много запросов. Попробуйте позже."
        else -> "Ошибка запроса (${response.status.value}): ${response.status.description}"
    }

    // HTTP ошибки сервера (5xx)
    is ServerResponseException -> "Сервер временно недоступен (${response.status.value}). Попробуйте позже."

    // Прочие HTTP ошибки
    is ResponseException -> "Ошибка сервера: ${response.status.value} ${response.status.description}"

    // IO ошибки
    is IOException -> "Ошибка сети: ${message ?: "неизвестная ошибка"}"

    // Всё остальное
    else -> "Неизвестная ошибка: ${message ?: this::class.simpleName}"
}

/**
 * Короткий код ошибки для аналитики/логов
 */
fun Throwable.toErrorCode(): String = when (this) {
    is HttpRequestTimeoutException -> "TIMEOUT"
    is SocketTimeoutException -> "SOCKET_TIMEOUT"
    is UnknownHostException -> "NO_INTERNET"
    is SSLHandshakeException -> "SSL_ERROR"
    is ClientRequestException -> "HTTP_${response.status.value}"
    is ServerResponseException -> "SERVER_${response.status.value}"
    is IOException -> "IO_ERROR"
    else -> this::class.simpleName ?: "UNKNOWN"
}