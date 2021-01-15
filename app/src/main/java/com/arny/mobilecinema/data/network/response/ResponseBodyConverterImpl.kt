package com.arny.mobilecinema.data.network.response

import okhttp3.ResponseBody
import okio.Buffer
import okio.GzipSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ResponseBodyConverterImpl @Inject constructor() : ResponseBodyConverter {
    override fun convert(res: ResponseBody, simpleText: Boolean, charset: Charset?): Document? {
        val origin = res.source().buffer
        var clone = origin.clone()
        return if (simpleText) {
            val response = clone.clone().readString(StandardCharsets.UTF_8)
            Jsoup.parse("<script>$response</script>")
        } else {
            GzipSource(clone.clone()).use { gzippedResponseBody ->
                clone = Buffer()
                clone.writeAll(gzippedResponseBody)
            }
            Jsoup.parse(clone.readString(charset ?: Charsets.UTF_8))
        }
    }
}
