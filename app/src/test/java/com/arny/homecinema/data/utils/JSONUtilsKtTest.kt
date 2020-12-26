package com.arny.homecinema.data.utils

import com.arny.homecinema.data.network.sources.getHlsListMap
import org.junit.jupiter.api.Test

internal class JSONUtilsKtTest {

    private companion object {
        const val hlslist = "{\"480\":\"https://480.m3u8\"," +
                "\"720\": \"https://720.m3u8\"}"
    }

    @Test
    fun `convert to map test`() {
        val qualityMap = getHlsListMap(hlslist)
        assert(qualityMap["480"]=="https://480.m3u8")
    }
}
