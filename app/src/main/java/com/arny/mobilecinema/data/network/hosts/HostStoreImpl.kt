package com.arny.mobilecinema.data.network.hosts

import com.arny.mobilecinema.data.repository.sources.prefs.Prefs
import com.arny.mobilecinema.data.repository.sources.prefs.PrefsConstants
import javax.inject.Inject

class HostStoreImpl @Inject constructor(
    private val prefs: Prefs
) : IHostStore {
    @Volatile
    override var host: String? = null
    override val baseUrl: String
        get() = baseUrls.find { s ->
            host == s.toHost()
        } ?: ""

    override val mainPageHeaders: Map<String, String?>
        get() = baseHeaders

    override val baseHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36",
            "Accept-Encoding" to "gzip",
            "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            "Connection" to "keep-alive",
        )

    override fun saveHost(source: String) {
        prefs.put(PrefsConstants.PREF_CURRENT_HOST, source)
    }

    override fun getCurrentHost(): String? = prefs.get<String>(PrefsConstants.PREF_CURRENT_HOST)

    internal companion object HOSTS {
        const val LORDFILM_AL_HOST = "al.lordfilms-s.pw"
        const val LORDFILM_ADA_HOST = "ada.lordsfilms.tube"
        const val ALL_TABFILL_HOST = "al.tabfil.me"
        const val ONLINE_VKINO_HOST = "onlinevkino.com"
        const val HOST_MOCK = "DemoContent"
    }

    override val baseUrls: List<String>
        get() = listOf(
            "http://al.lordfilms-s.pw/",
            "https://ada.lordsfilms.tube/",
            "http://al.tabfil.me/",
            "https://onlinevkino.com/",
        )

    override val availableHosts: List<String>
        get() = baseUrls.mapNotNull { it.toHost() } + listOf(
            HOST_MOCK,
        )

    private fun String.toHost() =
        "^https?://(.+)/".toRegex().find(this)?.groupValues?.getOrNull(1)
}
