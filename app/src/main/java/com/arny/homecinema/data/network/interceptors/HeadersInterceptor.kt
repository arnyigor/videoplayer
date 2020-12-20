package com.arny.homecinema.data.network.interceptors

import com.arny.homecinema.data.network.hosts.IHostStore
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class HeadersInterceptor(
    private val hostStore: IHostStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original: Request = chain.request()
        val newBuilder = original.newBuilder()
        for ((key, value) in hostStore.baseHeaders.entries) {
            newBuilder.addHeader(key, value)
        }
        return chain.proceed(
            newBuilder
                .method(original.method, original.body)
                .build()
        )
    }
}