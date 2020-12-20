package com.arny.homecinema.data.network.response

import okhttp3.ResponseBody
import org.jsoup.nodes.Document

interface ResponseBodyConverter {
    fun convert(res: ResponseBody): Document?
}
