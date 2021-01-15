package com.arny.mobilecinema.data.network.response

import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import java.nio.charset.Charset

interface ResponseBodyConverter {
    fun convert(
        res: ResponseBody,
        simpleText: Boolean = false,
        charset: Charset? = Charsets.UTF_8
    ): Document?
}
