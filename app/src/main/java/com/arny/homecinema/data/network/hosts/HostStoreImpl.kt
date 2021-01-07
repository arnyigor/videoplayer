package com.arny.homecinema.data.network.hosts

import com.arny.homecinema.data.repository.sources.prefs.Prefs
import com.arny.homecinema.data.repository.sources.prefs.PrefsConstants
import javax.inject.Inject

class HostStoreImpl @Inject constructor(
    private val prefs: Prefs
) : IHostStore {
    @Volatile
    override var host: String? = null
    override val baseUrl: String
        get() = host.toBaseUrl()

    override val mainPageHeaders: Map<String, String?>
        get() = baseHeaders

    override val baseHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36",
            "Accept-Encoding" to "gzip",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            "Connection" to "keep-alive",
        )

    override fun saveHost(source: String) {
        prefs.put(PrefsConstants.PREF_CURRENT_HOST, source)
    }

    override var savedHost: String?
        get() = prefs.get<String>(PrefsConstants.PREF_CURRENT_HOST)
        set(value) {}

    internal companion object HOSTS {
        const val LORDFILM_AL_HOST = "al.lordfilms-s.pw"
        const val LORDFILM_AL_BASE_URL = "http://$LORDFILM_AL_HOST/"
        const val HOST_MOCK = "TestData"
        const val HOST_MOCK2 = "TestData2"
    }

    override val availableHosts: List<String>
        get() = listOf(
            LORDFILM_AL_HOST,
            HOST_MOCK,
            HOST_MOCK2,
        )
}

internal fun String?.toBaseUrl(): String {
    return when (this) {
        HostStoreImpl.LORDFILM_AL_HOST -> HostStoreImpl.LORDFILM_AL_BASE_URL
        HostStoreImpl.HOST_MOCK -> "https://www.google.com/"
        HostStoreImpl.HOST_MOCK2 -> "https://www.google.com/"
        else -> HostStoreImpl.LORDFILM_AL_BASE_URL
    }
}
