package com.arny.videoplayer.data.network

import okhttp3.ResponseBody
import okio.Buffer
import okio.GzipSource
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

class ToStringConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, String> = Converter(::responseToStringConvert)

    private fun responseToStringConvert(res: ResponseBody): String {
        val origin = res.source().buffer
        var clone = origin.clone()
        GzipSource(clone.clone()).use { gzippedResponseBody ->
            clone = Buffer()
            clone.writeAll(gzippedResponseBody)
        }
        return clone.readString(StandardCharsets.UTF_8)
    }
}