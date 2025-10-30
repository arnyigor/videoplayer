package com.arny.mobilecinema.data.network.jsoup

class JsoupServiceHelper private constructor() {

    companion object {
        @Volatile
        private var instance: JsoupServiceHelper? = null

        fun getInstance(): JsoupServiceHelper {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = JsoupServiceHelper()
                    }
                }
            }
            return instance!!
        }
    }

    val cookie = mutableMapOf<String, String>()
    val userAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
        "Scheme" to "https",
        "Authority" to "ml.anwap.tube",
        "Cache-Control" to "max-age=0",
        "Sec-Fetch-Fest" to "document",
    )
}