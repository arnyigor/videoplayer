package com.arny.homecinema.data.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class HeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original: Request = chain.request()
        val request: Request = original.newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
            )
            .header(
                "Accept-Encoding", "gzip, deflate, br"
            )
            .header(
                "Connection", "keep-alive"
            )
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
            )
            .method(original.method, original.body)
            .build()
        return chain.proceed(request)
    }
}