package com.arny.videoplayer.data.network

import okhttp3.ResponseBody

interface ResponseBodyConverter {
    fun convert(res: ResponseBody): String
}
