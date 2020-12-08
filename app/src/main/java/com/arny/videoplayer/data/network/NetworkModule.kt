package com.arny.videoplayer.data.network

import com.arny.videoplayer.BuildConfig
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton


@Module
class NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConstants.VIDEO_BASE_URL)
            .addConverterFactory(ToStringConverterFactory())
            .client(client)
            .build()
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().writeTimeout(3, TimeUnit.MINUTES)
            .readTimeout(3, TimeUnit.MINUTES)
            .callTimeout(3, TimeUnit.MINUTES)
            .addInterceptor(getHttpInterceptor())
            .addInterceptor(Interceptor { chain ->
                val original: Request = chain.request()
                val request: Request = original.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
                    )
                    .header(
                        "Accept-Encoding", "gzip, deflate, br"
                    )
                    .header(
                        "Connection", "keep-alive"
                    )
                    .header(
                        "Content-Type", "text/html; charset=UTF-8;"
                    )
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
                    )
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            })
            .build()
    }

    private fun getHttpInterceptor() = if (BuildConfig.DEBUG)
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        } else HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): VideoApiService {
        return retrofit.create(VideoApiService::class.java)
    }
}