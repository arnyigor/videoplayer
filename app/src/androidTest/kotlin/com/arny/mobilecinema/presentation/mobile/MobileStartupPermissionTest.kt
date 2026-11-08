package com.arny.mobilecinema.presentation.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup must not be gated by optional permissions: the catalogue load used to be chained behind
 * notifications and WRITE_EXTERNAL_STORAGE, so denying them stopped the first screen.
 */
@RunWith(AndroidJUnit4::class)
class MobileStartupPermissionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName
    private val bottomNavId = instrumentation.targetContext.resources.getResourceEntryName(R.id.bottom_nav_view)

    /**
     * Permissions are revoked from the host before the run: `pm revoke` from inside the app process
     * kills that process together with the instrumentation.
     */
    @Before fun requireDeniedPrecondition() = DeviceGuard.requireApprovedDevice()

    private fun granted(permission: String) =
        instrumentation.targetContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Test fun homeWorksWithDeniedOptionalPermissionsAndNoPermissionDialog() {
        assertFalse("Precondition: storage must be denied", granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertFalse("Precondition: notifications must be denied", granted(Manifest.permission.POST_NOTIFICATIONS))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "Main screen did not appear with denied optional permissions",
                device.wait(Until.hasObject(By.res(packageName, bottomNavId)), 20_000),
            )
            assertFalse(
                "A system permission dialog was shown at startup",
                device.wait(Until.hasObject(By.pkg("com.android.permissioncontroller")), 2_000) ||
                    device.hasObject(By.pkg("com.google.android.permissioncontroller")),
            )
            scenario.onActivity {
                assertTrue("MainActivity is not resumed", it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                assertFalse("MainActivity is finishing", it.isFinishing)
            }
        }
        // Denial must stay denied: the screen must not have escalated to a permission flow.
        assertFalse("Storage permission was requested behind the user's back",
            granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
}
