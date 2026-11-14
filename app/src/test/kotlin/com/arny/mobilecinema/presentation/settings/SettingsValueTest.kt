package com.arny.mobilecinema.presentation.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsValueTest {
    @Test fun `existing default and allowed endpoints are preserved in millibels`() {
        listOf(100, 1000, 100000).forEach { value ->
            assertEquals(value, parseVolumeBoost(value.toString()))
        }
    }

    @Test fun `out of range values cannot overwrite the stored preference`() {
        listOf("0", "99", "100001", "-100").forEach { assertNull(parseVolumeBoost(it)) }
    }

    @Test fun `blank or missing value is rejected`() {
        listOf(null, "", " ", " 1000 ").forEach { assertNull(parseVolumeBoost(it)) }
    }

    @Test fun `non integer input is rejected`() {
        listOf("10.5", "10,5", "loud", "1e3").forEach { assertNull(parseVolumeBoost(it)) }
    }

    @Test fun `integer overflow is rejected without throwing`() {
        assertNull(parseVolumeBoost("99999999999999999999999999"))
    }
}
