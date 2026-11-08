package com.arny.mobilecinema.presentation.poster

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.compose.ComposeTestActivity
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileContinueCard
import com.arny.mobilecinema.presentation.mobile.ui.components.MobilePosterCard
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme
import com.arny.mobilecinema.presentation.tv.ui.components.ContinueWatchingCard
import com.arny.mobilecinema.presentation.tv.ui.components.PosterCard
import com.arny.mobilecinema.presentation.tv.ui.components.TvHero
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Both Compose design systems must decode the same browser-protected image, not just show a box. */
class PosterComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()
    private lateinit var server: PosterServer

    @Before fun setup() {
        DeviceGuard.requireApprovedDevice()
        assertTrue("Poster UI tests require an isolated APK", compose.activity.packageName.endsWith(".perfdebug") ||
            compose.activity.packageName.endsWith(".tvdebug"))
        server = PosterServer()
    }

    @After fun close() { if (::server.isInitialized) server.close() }

    private fun images(): List<ImageView> {
        val result = mutableListOf<ImageView>()
        fun visit(view: View) {
            if (view is ImageView && view.id == R.id.poster_image) result += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(compose.activity.findViewById(android.R.id.content))
        return result
    }

    private fun awaitLoaded(count: Int) {
        compose.waitUntil(15_000) {
            compose.runOnIdle {
                val views = images()
                views.size == count && views.all { bitmapColor(it.drawable) == server.red && it.width > 0 && it.height > 0 }
            }
        }
        compose.runOnIdle {
            assertEquals(count, images().size)
            assertTrue("Poster stole TV focus", images().none { it.isFocusable || it.isClickable })
        }
    }

    @Test fun mobileAndTvPosterCardsLoadAndActuallyPaintTheImage() {
        compose.runOnUiThread {
            compose.activity.setContent {
                Row {
                    MobileTheme {
                        MobilePosterCard("Phone poster", server.origin + "/poster.png", {}, Modifier.width(100.dp))
                    }
                    TvTheme {
                        PosterCard("TV poster", server.origin + "/poster.png", {}, Modifier.width(130.dp))
                    }
                }
            }
        }
        awaitLoaded(2)
        Thread.sleep(400) // Finish the bounded Glide crossfade before reading actual screen pixels.
        val locations = compose.runOnIdle { images().map { view ->
            val xy = IntArray(2)
            view.getLocationOnScreen(xy)
            (xy[0] + view.width / 2) to (xy[1] + view.height / 4)
        } }
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            for ((x, y) in locations) {
                assertTrue("Poster is outside the visible screen", x in 0 until screenshot.width && y in 0 until screenshot.height)
                val pixel = screenshot.getPixel(x, y)
                assertTrue("Decoded image was not painted on screen: $pixel",
                    Color.red(pixel) > 100 && Color.red(pixel) > Color.green(pixel) * 1.5)
            }
        } finally { screenshot.recycle() }
        assertTrue("Neither card reached the image origin", server.requests.isNotEmpty())
    }

    @Test fun continueWatchingCardsAndTvHeroUseTheSameImagePipeline() {
        compose.runOnUiThread {
            compose.activity.setContent {
                Column {
                    Row {
                        MobileTheme {
                            MobileContinueCard("Phone continue", server.origin + "/poster.png", null, null, null,
                                {}, Modifier.width(120.dp))
                        }
                        TvTheme {
                            ContinueWatchingCard("TV continue", null, server.origin + "/poster.png", null,
                                {}, Modifier.width(120.dp))
                        }
                    }
                    TvTheme {
                        TvHero("Hero", "", "", server.origin + "/poster.png", "Play", {}, "Details", {})
                    }
                }
            }
        }
        awaitLoaded(3)
    }
}
