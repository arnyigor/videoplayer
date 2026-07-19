package com.arny.mobilecinema.presentation.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arny.mobilecinema.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateServiceIntegrationTest {

    @Test
    fun relativeUpdateUrlIsNormalizedOnDevice() {
        val url = normalizeUpdateUrl(
            url = "/films/37033",
            entryPointBaseUrl = BuildConfig.BASE_LINK,
            savedBaseUrl = "https://anwap.media"
        )

        assertEquals("https://my.anwap.love/films/37033", url)
    }
}
