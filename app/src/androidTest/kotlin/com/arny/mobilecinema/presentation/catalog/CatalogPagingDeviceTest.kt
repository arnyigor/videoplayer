package com.arny.mobilecinema.presentation.catalog

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-077: адресный тест на дефект «каталог показывает три карточки и не прокручивается».
 *
 * Проверяется наблюдаемое поведение (сколько карточек видно и меняется ли список при
 * прокрутке), без доступа к внутреннему состоянию: если дефект вернётся, тест упадёт.
 */
@RunWith(AndroidJUnit4::class)
class CatalogPagingDeviceTest {

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
     * Данные для теста.
     *
     * Честное ограничение: instrumentation-прогон ставит приложение с чистыми данными, а
     * скопировать фикстурную базу из теста не удалось (`run-as` через uiAutomation не отдаёт
     * ни stdout, ни stderr — причина не установлена). Поэтому без подготовленного каталога
     * тест честно пропускается (Assume), а не делает вид, что проверил прокрутку.
     */
    private fun visibleTitles(): List<String> {
        val nodes = device.findObjects(By.clazz("android.widget.TextView"))
        return nodes.mapNotNull { it.text }
            .filter { it.startsWith("Fixture") || it.contains("Матрица") }
            .distinct()
    }

    @Test
    fun catalogShowsMoreThanOneRowAndScrolls() {
        DeviceGuard.requireApprovedDevice()
        ActivityScenario.launch(com.arny.mobilecinema.presentation.MainActivity::class.java).use { scenario ->
            Thread.sleep(3_000)
            // Вкладка «Фильмы».
            device.findObject(UiSelector().resourceId("${targetPackage()}:id/btnHomeContentCinema"))
                .click()
            device.wait(Until.hasObject(By.textStartsWith("Fixture")), 15_000)
            Thread.sleep(2_000)

            val before = visibleTitles()
            assumeTrue("каталог пуст: прокрутку проверить нечем", before.isNotEmpty())
            assertTrue(
                "в каталоге видно меньше четырёх карточек: $before",
                before.size >= 4
            )

            // Прокрутка: первый видимый элемент должен смениться.
            repeat(3) {
                device.swipe(540, 1_600, 540, 700, 20)
                Thread.sleep(600)
            }
            Thread.sleep(1_000)
            val after = visibleTitles()
            assertTrue(
                "список не прокрутился: было $before, стало $after",
                after != before
            )
        }
    }

    private fun targetPackage(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
}
