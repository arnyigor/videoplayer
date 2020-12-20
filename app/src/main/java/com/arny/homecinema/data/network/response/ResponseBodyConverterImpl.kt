package com.arny.homecinema.data.network.response

import okhttp3.ResponseBody
import okio.Buffer
import okio.GzipSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ResponseBodyConverterImpl @Inject constructor() : ResponseBodyConverter {
    override fun convert(res: ResponseBody): Document? {
        val origin = res.source().buffer
        var clone = origin.clone()
        GzipSource(clone.clone()).use { gzippedResponseBody ->
            clone = Buffer()
            clone.writeAll(gzippedResponseBody)
        }
        return Jsoup.parse(clone.readString(StandardCharsets.UTF_8))
    }
}
