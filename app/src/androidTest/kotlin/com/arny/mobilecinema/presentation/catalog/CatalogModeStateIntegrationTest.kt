package com.arny.mobilecinema.presentation.catalog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Existing real-catalogue emulator check. Never seeds or clears user data. */
@RunWith(AndroidJUnit4::class)
class CatalogModeStateIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun filmSortAndGenreDoNotLeakIntoSeries() {
        com.arny.mobilecinema.presentation.DeviceGuard.requireApprovedDevice()
        onView(withId(R.id.btnHomeContentCinema)).perform(click())
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Сортировка: Новинки").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Сортировка: Новинки").performClick()
        compose.onNodeWithText("По рейтингу").performClick()
        compose.onNodeWithText("Драмы").performClick()
        compose.onNodeWithText("Драмы").assertIsSelected()

        onView(withId(R.id.btnHomeContentSerial)).perform(click())
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Сортировка: Новинки").fetchSemanticsNodes().isNotEmpty()
        }
        // Genre chips live in a LazyRow: the default "Все" chip may not be composed after
        // the Films selection scrolled the row. Assert instead that the Films genre is gone.
        compose.onAllNodes(hasText("Драмы") and isSelected()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Сортировка: Новинки").performClick()
        compose.onNodeWithText("По алфавиту").performClick()

        onView(withId(R.id.btnHomeContentCinema)).perform(click())
        compose.onNodeWithContentDescription("Сортировка: По рейтингу").assertExists()
        compose.onNodeWithText("Драмы").assertIsSelected()
    }
}
