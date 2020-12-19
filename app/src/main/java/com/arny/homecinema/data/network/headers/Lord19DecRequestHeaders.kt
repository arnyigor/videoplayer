package com.arny.homecinema.data.network.headers

import com.arny.homecinema.data.network.IHostStore

class Lord19DecRequestHeaders(
    private val hostStore: IHostStore
) : IRequestHeaders {
    override val iFrameHeaders: Map<String, String>
        get() = emptyMap()
    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to hostStore.baseUrl
        )
}