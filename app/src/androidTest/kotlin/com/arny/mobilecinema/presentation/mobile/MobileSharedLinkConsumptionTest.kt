package com.arny.mobilecinema.presentation.mobile

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.splash.StartActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared link used to travel through a SharedFlow without replay and could be dropped before
 * the home screen subscribed. The contract now is: the extra is consumed exactly once and cleared.
 */
@RunWith(AndroidJUnit4::class)
class MobileSharedLinkConsumptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun offline() {
        DeviceGuard.requireApprovedDevice()
        shell("svc wifi disable")
        shell("svc data disable")
    }

    @After fun online() {
        shell("svc wifi enable")
        shell("svc data enable")
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun intentWith(url: String): Intent =
        Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
            .putExtra(StartActivity.KEY_SHARED_URL, url)

    private fun waitForExtraConsumed(scenario: ActivityScenario<MainActivity>, timeoutMs: Long = 30_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var remaining: String? = "not read"
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { remaining = it.intent.getStringExtra(StartActivity.KEY_SHARED_URL) }
            if (remaining == null) return null
            Thread.sleep(250)
        }
        return remaining
    }

    @Test fun coldStartConsumesLinkEvenWhenFetchFails() {
        ActivityScenario.launch<MainActivity>(intentWith("https://mm.anwap.media/films/49057")).use { scenario ->
            // Only handleSharedUrl removes the extra, so its absence proves the link was consumed.
            assertNull("Shared link extra was never consumed (offline fetch must still finish)",
                waitForExtraConsumed(scenario))
            scenario.onActivity {
                // No crash and no stuck state: the app stays on the start destination.
                assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, it.lifecycle.currentState)
                assertNull("Extra must be removed exactly once", it.intent.getStringExtra(StartActivity.KEY_SHARED_URL))
            }
        }
    }

}
