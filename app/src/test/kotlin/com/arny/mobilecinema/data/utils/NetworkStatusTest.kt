package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Регресс на текст ошибки плеера: отсутствие сети и «сеть есть, интернета нет»
 * должны различаться, иначе пользователь с captive portal видит неверную причину.
 */
class NetworkStatusTest {

    @Test
    fun `no connection wins over validation flag`() {
        assertEquals(NetworkStatus.NONE, networkStatus(ConnectionType.NONE, validated = false))
        assertEquals(NetworkStatus.NONE, networkStatus(ConnectionType.NONE, validated = true))
    }

    @Test
    fun `connected but unvalidated network is reported separately`() {
        assertEquals(NetworkStatus.UNVALIDATED, networkStatus(ConnectionType.WIFI(100_000), validated = false))
        assertEquals(NetworkStatus.UNVALIDATED, networkStatus(ConnectionType.ETHERNET(100_000), validated = false))
        assertEquals(NetworkStatus.UNVALIDATED, networkStatus(ConnectionType.MOBILE(10_000), validated = false))
    }

    @Test
    fun `validated network is online for every transport including ethernet`() {
        listOf(
            ConnectionType.WIFI(100_000),
            ConnectionType.ETHERNET(100_000),
            ConnectionType.MOBILE(10_000),
            ConnectionType.VPN(100_000, isMetered = false),
        ).forEach { connection ->
            assertEquals(NetworkStatus.ONLINE, networkStatus(connection, validated = true), connection.toString())
        }
    }

    @Test
    fun `ethernet without validation is not reported as no connection`() {
        assertEquals(NetworkStatus.UNVALIDATED, networkStatus(ConnectionType.ETHERNET(0), validated = false))
    }
}
