package com.arny.videoplayer.data.network

import okhttp3.ResponseBody
import org.jsoup.nodes.Document

interface ResponseBodyConverter {
    fun convert(res: ResponseBody): Document?
}
