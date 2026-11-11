package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionTypePolicyTest {

    @Test
    fun `ethernet allows unmetered operations`() {
        assertTrue(ConnectionType.ETHERNET(100_000).allowsUnmeteredOperations())
    }

    @Test
    fun `wifi and explicitly unmetered vpn allow background operations`() {
        assertTrue(ConnectionType.WIFI(100_000).allowsUnmeteredOperations())
        assertTrue(ConnectionType.VPN(100_000, isMetered = false).allowsUnmeteredOperations())
    }

    @Test
    fun `metered and unknown vpn require confirmation`() {
        assertFalse(ConnectionType.VPN(100_000, isMetered = true).allowsUnmeteredOperations())
        assertFalse(ConnectionType.VPN(100_000).allowsUnmeteredOperations())
    }

    @Test
    fun `mobile and no connection do not allow unmetered operations`() {
        assertFalse(ConnectionType.MOBILE(10_000).allowsUnmeteredOperations())
        assertFalse(ConnectionType.NONE.allowsUnmeteredOperations())
    }
}
