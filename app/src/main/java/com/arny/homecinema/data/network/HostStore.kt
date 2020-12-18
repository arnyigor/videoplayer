package com.arny.homecinema.data.network

import javax.inject.Inject

class HostStore @Inject constructor() : IHostStore {
    override var host: String? = null

    companion object HOSTS {
        const val LORDFILM_AL_HOST = "al.lordfilms-s.pw"
        const val LORDFILM_AL_BASE_URL = "http://$LORDFILM_AL_HOST/"
        const val LORDFILM_20_ZONE_HOST = "lord-filmds20s.lordfilm1.zone"
        const val LORDFILM_20_ZONE_BASE_URL = "https://$LORDFILM_20_ZONE_HOST/"
    }
}

internal fun String.toBaseUrl(): String {
    return when (this) {
        HostStore.LORDFILM_AL_HOST -> HostStore.LORDFILM_AL_BASE_URL
        HostStore.LORDFILM_20_ZONE_HOST -> HostStore.LORDFILM_20_ZONE_BASE_URL
        else -> HostStore.LORDFILM_AL_BASE_URL
    }
}