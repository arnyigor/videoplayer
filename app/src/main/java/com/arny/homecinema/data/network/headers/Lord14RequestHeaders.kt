package com.arny.homecinema.data.network.headers

class Lord14RequestHeaders : IRequestHeaders {
    override val iFrameHeaders: Map<String, String>
        get() = mapOf(
            "Host" to "api.placehere.link",
//            "Referer" to "https://api1589486608.placehere.link/",
        )
    override val detailHeaders: Map<String, String>
        get() = emptyMap()
}