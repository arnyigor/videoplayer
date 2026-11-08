package com.arny.mobilecinema.presentation.settings

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.DataUpdateResult
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the UI stays usable while a catalogue update check is in flight.
 *
 * The response is held deliberately, so the assertions do not depend on network timing and
 * the check can never download anything. No preference, database row or catalogue entry is
 * modified: the check runs on a test-owned ViewModel, not on the settings screen.
 */
@RunWith(AndroidJUnit4::class)
class CatalogCheckResponsivenessTest {
    private lateinit var original: DataUpdateInteractor
    private val release = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)

    @Before
    fun holdUpdateResponse() {
        DeviceGuard.requireApprovedDevice()
        // Fresh Gradle test installs have no runtime grants; a system dialog pauses MainActivity.
        if (Build.VERSION.SDK_INT < 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val command = "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.WRITE_EXTERNAL_STORAGE}"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
                it.readBytes()
            }
        }
        original = GlobalContext.get().get()
        val held = object : DataUpdateInteractor by original {
            override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>> =
                flow {
                    started.set(true)
                    release.await()
                    emit(DataResult.Success(DataUpdateResult("")))
                }
        }
        loadKoinModules(module { single<DataUpdateInteractor> { held } })
    }

    @After
    fun restoreRealInteractor() {
        release.complete(Unit)
        loadKoinModules(module { single<DataUpdateInteractor> { original } })
    }

    @Test
    fun canNavigateWhileCatalogCheckIsUnfinished() {
        val sync = GlobalContext.get().get<CatalogSyncViewModel>()
        ActivityScenario.launch(MainActivity::class.java).use {
            sync.check()
            val deadline = System.currentTimeMillis() + 5_000
            while (!started.get() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue("The check must have started", started.get())
            val stateDeadline = System.currentTimeMillis() + 2_000
            while (!sync.state.value.isChecking && System.currentTimeMillis() < stateDeadline) {
                Thread.sleep(20)
            }
            assertTrue(
                "Check state must be reporting progress, was ${sync.state.value}",
                sync.state.value.isChecking
            )
            assertFalse("Response is deliberately held", release.isCompleted)

            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.tiedtSiteSearch)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_history)).perform(click())
            onView(withId(R.id.rvHistoryList)).check(matches(isDisplayed()))

            assertFalse("Navigation must not wait for the check", release.isCompleted)
            assertTrue("Check must still be in progress in the background", sync.state.value.isChecking)
        }
    }
}
