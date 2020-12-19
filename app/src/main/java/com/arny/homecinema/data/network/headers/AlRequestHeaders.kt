package com.arny.homecinema.data.network.headers

import com.arny.homecinema.data.network.IHostStore

class AlRequestHeaders(private val hostStore: IHostStore) : IRequestHeaders {
    override val iFrameHeaders: Map<String, String>
        get() = mapOf(
            "Host" to "apilordfilms-s.multikland.net",
        )

    override val detailHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to "${hostStore.baseUrl}index.php"
        )
}