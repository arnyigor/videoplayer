package com.arny.homecinema.data.network.hosts

import javax.inject.Inject

class HostStoreImpl @Inject constructor() : IHostStore {
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

    internal companion object HOSTS {
        const val LORDFILM_AL_HOST = "al.lordfilms-s.pw"
        const val LORDFILM_AL_BASE_URL = "http://$LORDFILM_AL_HOST/"
        const val LORDFILM_KINO_I_HOST = "kino-i.online"
        const val LORDFILM_KINO_I_BASE_URL = "https://$LORDFILM_KINO_I_HOST/"
        const val LORDFILM_23s_HOST = "lord-filmds23s.lordfilm1.zone"
        const val LORDFILM_23s_BASE_URL = "https://$LORDFILM_23s_HOST/"
    }

    override val availableHosts: List<String>
        get() = listOf(
            LORDFILM_AL_HOST,
            LORDFILM_KINO_I_HOST,
            LORDFILM_23s_HOST
        )
}

internal fun String?.toBaseUrl(): String {
    return when (this) {
        HostStoreImpl.LORDFILM_AL_HOST -> HostStoreImpl.LORDFILM_AL_BASE_URL
        HostStoreImpl.LORDFILM_KINO_I_HOST -> HostStoreImpl.LORDFILM_KINO_I_BASE_URL
        HostStoreImpl.LORDFILM_23s_HOST -> HostStoreImpl.LORDFILM_23s_BASE_URL
        else -> HostStoreImpl.LORDFILM_AL_BASE_URL
    }
}