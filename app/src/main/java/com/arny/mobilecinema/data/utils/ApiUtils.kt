package com.arny.mobilecinema.data.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import java.net.URI
import javax.net.ssl.SSLHandshakeException

sealed class ConnectionType(open val speedKbps: Int) {
    object NONE : ConnectionType(0)
    data class MOBILE(val speed: Int) : ConnectionType(speed)
    data class WIFI(val speed: Int) : ConnectionType(speed)
    data class VPN(val speed: Int) : ConnectionType(speed)
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

fun getFullError(throwable: Throwable): String {
    throwable.printStackTrace()
    var error: String
    val code: Int
    try {
        when (throwable) {
//            is HttpException -> {
//                code = throwable.code()
//                error = throwable.response()?.errorBody()?.string().toString()
//                when (code) {
//                    500 -> error = "Внутренняя ошибка сервера"
//                    504 -> error = "Время ожидания истекло, повторите запрос позже"
//                    503 -> error = "Сервис временно недоступен, повторите запрос позже"
//                    403 -> error = "Сервис заблокирован"
//                    404 -> error = "Страница не найдена"
//                }
//            }
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
