package com.arny.mobilecinema.presentation.a11y

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-078: проверка разметки на «немые» кликабельные узлы — то, что читает TalkBack.
 *
 * Настоящий TalkBack на этом стенде недоступен (пакет не установлен, учётной записи Play нет),
 * поэтому проверяется то, что можно проверить детерминированно: у каждого кликабельного узла на
 * ключевых экранах есть текст или contentDescription, и у иконочных кнопок есть описание.
 */
@RunWith(AndroidJUnit4::class)
class A11yLabelCoverageTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun grantStorage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(
            "pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE",
            "pm grant $packageName android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
    }

    /**
     * TalkBack читает и потомков, поэтому узел «немой» только если ни у него, ни у его детей
     * нет ни текста, ни описания.
     */
    private fun hasLabel(node: androidx.test.uiautomator.UiObject2): Boolean {
        if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) return true
        return node.children.any { hasLabel(it) }
    }

    private fun unnamedClickables(): List<String> =
        device.findObjects(By.clickable(true))
            .filterNot { hasLabel(it) }
            .map { node -> "${node.className} ${node.visibleBounds}" }

    private fun assertScreenLabelled(screen: String) {
        val unnamed = unnamedClickables()
        assertTrue("$screen: кликабельные узлы без имени: $unnamed", unnamed.isEmpty())
    }

    @Test
    fun keyScreensHaveNoUnnamedClickableNodes() {
        DeviceGuard.requireApprovedDevice()
        ActivityScenario.launch(com.arny.mobilecinema.presentation.MainActivity::class.java).use { _ ->
            Thread.sleep(3_000)
            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            assertTrue(device.wait(Until.hasObject(By.res(packageName, "btnHomeContentAll")), 10_000))
            assertScreenLabelled("главная")

            // Каталог.
            device.findObject(By.res(packageName, "btnHomeContentCinema")).click()
            Thread.sleep(2_500)
            assertScreenLabelled("каталог")

            // Поиск.
            device.findObject(By.desc("Поиск")).click()
            Thread.sleep(2_500)
            assertScreenLabelled("поиск")
        }
    }
}
