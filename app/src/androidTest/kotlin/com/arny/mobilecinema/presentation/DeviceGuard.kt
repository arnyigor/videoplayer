package com.arny.mobilecinema.presentation

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

/**
 * These scenarios run on the emulator by default because they touch an existing catalogue.
 * A physical device is allowed only when the operator passes the instrumentation argument
 * `-e allowPhysical true`; data is still never cleared or replaced.
 */
object DeviceGuard {
    fun requireApprovedDevice() {
        val isEmulator = Build.HARDWARE in listOf("ranchu", "goldfish")
        val allowPhysical = InstrumentationRegistry.getArguments()
            .getString("allowPhysical")?.toBoolean() == true
        if (!isEmulator && !allowPhysical) {
            throw org.junit.AssumptionViolatedException(
                "Physical device requires -e allowPhysical true"
            )
        }
    }
}
