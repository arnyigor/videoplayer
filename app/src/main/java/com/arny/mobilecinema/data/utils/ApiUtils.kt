@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.data.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import com.arny.mobilecinema.data.models.DataThrowable
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLHandshakeException

fun getDomainName(url: String): String = try {
    val uri = URI(url)
    val scheme = uri.scheme?.takeIf { it.isNotBlank() }
    val host = uri.host?.takeIf { it.isNotBlank() }
    val port = uri.port.takeIf { it > 0 }

    if (scheme != null && host != null) {
        "$scheme://$host${port?.let { ":$it" }.orEmpty()}"
    } else ""
} catch (e: Exception) {
    ""
}

fun String.getWithDomain(location: String): String {
    val url = trim()
    return when {
        url.isBlank() -> ""
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true) -> url
        url.startsWith("//") -> "${if (location.trim().startsWith("http://", ignoreCase = true)) "http" else "https"}:$url"
        else -> {
            // Без настроенного baseUrl постеры оставались пустыми: берём домен по умолчанию.
            val domain = getDomainName(location).trimEnd('/')
                .ifBlank { com.arny.mobilecinema.data.repository.AppConstants.ENTRY_POINT_URL.trimEnd('/') }
            val path = url.trimStart('/')
            if (domain.isNotBlank() && path.isNotBlank()) {
                "$domain/$path"
            } else {
                ""
            }
        }
    }
}

sealed class ConnectionType(open val speedKbps: Int) {
    object NONE : ConnectionType(0)
    data class MOBILE(override val speedKbps: Int) : ConnectionType(speedKbps)
    data class WIFI(override val speedKbps: Int) : ConnectionType(speedKbps)
    data class ETHERNET(override val speedKbps: Int) : ConnectionType(speedKbps)
    data class VPN(
        override val speedKbps: Int,
        val isMetered: Boolean = true,
    ) : ConnectionType(speedKbps)
}

/** Returns the active transport; Ethernet is a valid unmetered connection, not NONE. */
fun getConnectionType(context: Context): ConnectionType {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return ConnectionType.NONE
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        ?: return ConnectionType.NONE
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return ConnectionType.NONE
    }
    val downSpeedKbps = capabilities.linkDownstreamBandwidthKbps

    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
            ConnectionType.VPN(downSpeedKbps,
                isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            ConnectionType.ETHERNET(downSpeedKbps)

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            ConnectionType.WIFI(downSpeedKbps)

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            ConnectionType.MOBILE(downSpeedKbps)

        else -> ConnectionType.NONE
    }
}

fun ConnectionType.allowsUnmeteredOperations(): Boolean = when (this) {
    is ConnectionType.WIFI,
    is ConnectionType.ETHERNET -> true
    is ConnectionType.VPN -> !isMetered
    else -> false
}

/** Состояние сети для текста ошибки: нет сети / сеть есть, но интернета нет / всё хорошо. */
enum class NetworkStatus {
    NONE,
    UNVALIDATED,
    ONLINE,
}

/** Чистое рещение по статусу: позволяет проверить тексты ошибок без Android. */
fun networkStatus(connection: ConnectionType, validated: Boolean): NetworkStatus = when {
    connection is ConnectionType.NONE -> NetworkStatus.NONE
    !validated -> NetworkStatus.UNVALIDATED
    else -> NetworkStatus.ONLINE
}

/** Подтверждённый доступ в интернет (captive portal и мёртвый роутер дают false). */
fun hasValidatedInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun getMaxSpeedKbps(it: NetworkInfo): Int = when (it.subtype) {
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_IDEN,
    TelephonyManager.NETWORK_TYPE_GSM -> 108

    TelephonyManager.NETWORK_TYPE_UMTS,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_HSPAP,
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> 23000

    TelephonyManager.NETWORK_TYPE_LTE,
    TelephonyManager.NETWORK_TYPE_IWLAN,
    19 -> 50000

    else -> 0
}

fun getErrorUrl(throwable: Throwable): String? {
    return ((throwable as? PlaybackException)?.cause as? HttpDataSourceException)?.dataSpec?.key
}

fun getFullError(throwable: Throwable, context: Context? = null): String {
    throwable.printStackTrace()
    var error: String = throwable.message.orEmpty()
    val code: Int
    try {
        when (throwable) {
            is DataThrowable -> {
                error = context?.getString(throwable.errorRes).orEmpty()
            }

            is TimeoutCancellationException -> {
                error = "Время ожидания истекло"
            }

            is PlaybackException -> {
                when (val cause = throwable.cause) {
                    is HttpDataSource.InvalidResponseCodeException -> {
                        code = cause.responseCode
                        val url = cause.dataSpec.key
                        when (code) {
                            500 -> error = "Внутренняя ошибка сервера $url"
                            504 -> error = "Время ожидания истекло, повторите запрос позже $url"
                            503 -> error = "Сервис временно недоступен, повторите запрос позже $url"
                            403 -> error = "Доступ к запрошенному ресурсу запрещён $url"
                            404 -> error = "Страница не найдена $url"
                        }
                    }
                    is HttpDataSourceException->{
                        val url = cause.dataSpec.key
                        when (val cause = cause.cause) {
                            is HttpDataSource.InvalidResponseCodeException -> {
                                code = cause.responseCode
                                when (code) {
                                    500 -> error = "Внутренняя ошибка сервера $url"
                                    504 -> error = "Время ожидания истекло, повторите запрос позже $url"
                                    503 -> error = "Сервис временно недоступен, повторите запрос позже $url"
                                    403 -> error = "Доступ к запрошенному ресурсу запрещён $url"
                                    404 -> error = "Страница не найдена $url"
                                }
                            }
                            is SSLHandshakeException -> {
                                error = "Ошибка сертификата $url"
                            }
                            else -> {
                                error = throwable.message.orEmpty()
                            }
                        }
                    }

                    is UnrecognizedInputFormatException -> {
                        error = "${cause.uri} ${cause.message}"
                    }

                    is SSLHandshakeException -> {
                        error = "Ошибка сертификата"
                    }

                    else -> {
                        error = cause?.message.toString()
                    }
                }
            }

            is HttpDataSourceException -> {
                error = throwable.message.orEmpty()
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
