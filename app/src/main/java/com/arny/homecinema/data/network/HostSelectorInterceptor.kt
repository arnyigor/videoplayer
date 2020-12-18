package com.arny.homecinema.data.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class HostSelectorInterceptor @Inject constructor(
    private val hostStore: IHostStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request: Request = chain.request()
        val reqUrl: String = request.url.host
        val host = hostStore.host
        if (host != null && reqUrl != host) {
            val newUrl: HttpUrl = request.url.newBuilder()
                .host(host)
                .build()
            request = request.newBuilder()
                .url(newUrl)
                .build()
        }
        return chain.proceed(request)
    }
}
