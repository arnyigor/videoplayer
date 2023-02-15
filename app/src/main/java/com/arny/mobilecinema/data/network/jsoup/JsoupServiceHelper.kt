package com.arny.mobilecinema.data.network.jsoup

object JsoupServiceHelper {
    val cookie = mutableMapOf<String, String>()
    const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
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