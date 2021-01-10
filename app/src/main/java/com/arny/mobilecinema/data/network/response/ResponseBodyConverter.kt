package com.arny.mobilecinema.data.network.response

import okhttp3.ResponseBody
import org.jsoup.nodes.Document

interface ResponseBodyConverter {
    fun convert(res: ResponseBody): Document?
}
