package com.arny.homecinema.data.network

import javax.inject.Inject

class HostStore @Inject constructor() : IHostStore {
    override var host: String? = null
    override val baseUrl: String
        get() = host.toBaseUrl()

    override val mainPageHeaders: Map<String, String?>
        get() = when (host) {
            LORDFILM_14_ZONE_HOST -> {
                 baseHeaders
            }
            else -> baseHeaders
        }
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
        const val LORDFILM_20_ZONE_HOST = "lord-filmds20s.lordfilm1.zone"
        const val LORDFILM_20_ZONE_BASE_URL = "https://$LORDFILM_20_ZONE_HOST/"
        const val LORDFILM_14_ZONE_HOST = "lord-filmds60s.lordfilm1.zone"
        const val LORDFILM_14_ZONE_BASE_URL = "https://$LORDFILM_14_ZONE_HOST/"
    }
}

internal fun String?.toBaseUrl(): String {
    return when (this) {
        HostStore.LORDFILM_AL_HOST -> HostStore.LORDFILM_AL_BASE_URL
        HostStore.LORDFILM_20_ZONE_HOST -> HostStore.LORDFILM_20_ZONE_BASE_URL
        HostStore.LORDFILM_14_ZONE_HOST -> HostStore.LORDFILM_14_ZONE_BASE_URL
        else -> HostStore.LORDFILM_AL_BASE_URL
    }
}