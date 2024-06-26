package com.arny.mobilecinema.data.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import com.arny.mobilecinema.data.models.DataThrowable
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLHandshakeException

fun getDomainName(url: String): String = try {
    val uri = URI(url)
    val scheme: String? = uri.scheme
    val host: String? = uri.host
    if (!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
        "${scheme}://${host}"
    } else {
        ""
    }
} catch (e: Exception) {
    ""
}

fun String.getWithDomain(location: String): String =
    when {
        this.startsWith("http") -> this
        else -> "${getDomainName(location)}$this"
    }

fun urlEncode(value: String): String? = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

sealed class ConnectionType(open val speedKbps: Int) {
    object NONE : ConnectionType(0)
    data class MOBILE(override val speedKbps: Int) : ConnectionType(speedKbps)
    data class WIFI(override val speedKbps: Int) : ConnectionType(speedKbps)
    data class VPN(override val speedKbps: Int) : ConnectionType(speedKbps)
}

fun getConnectionType(context: Context): ConnectionType {
    var type: ConnectionType = ConnectionType.NONE
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        cm?.let {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            val downSpeedKbps = (capabilities?.linkDownstreamBandwidthKbps) ?: 0
            capabilities?.run {
                when {
                    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        type = ConnectionType.WIFI(downSpeedKbps)
                    }

                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        type = ConnectionType.MOBILE(downSpeedKbps)
                    }

                    hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                        type = ConnectionType.VPN(downSpeedKbps)
                    }
                }
            }
        }
    } else {
        cm?.let {
            cm.activeNetworkInfo?.let {
                when (it.type) {
                    ConnectivityManager.TYPE_WIFI -> {
                        type = ConnectionType.WIFI(0)
                    }

                    ConnectivityManager.TYPE_MOBILE -> {
                        type = ConnectionType.MOBILE(getMaxSpeedKbps(it))
                    }

                    ConnectivityManager.TYPE_VPN -> {
                        type = ConnectionType.VPN(0)
                    }
                }
            }
        }
    }
    return type
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

            is ExoPlaybackException -> {
                when (val sourceException = throwable.sourceException) {
                    is HttpDataSource.InvalidResponseCodeException -> {
                        code = sourceException.responseCode
                        val url = sourceException.dataSpec.key
                        when (code) {
                            500 -> error = "Внутренняя ошибка сервера $url"
                            504 -> error = "Время ожидания истекло, повторите запрос позже $url"
                            503 -> error = "Сервис временно недоступен, повторите запрос позже $url"
                            403 -> error = "Доступ к запрошенному ресурсу запрещён $url"
                            404 -> error = "Страница не найдена $url"
                        }
                    }
                    is HttpDataSourceException->{
                        val url = sourceException.dataSpec.key
                        when (val cause = sourceException.cause) {
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
                        error = "${sourceException.uri} ${sourceException.message}"
                    }

                    is SSLHandshakeException -> {
                        error = "Ошибка сертификата"
                    }

                    else -> {
                        error = sourceException.message.toString()
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
