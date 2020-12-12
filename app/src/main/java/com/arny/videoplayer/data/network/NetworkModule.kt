package com.arny.videoplayer.data.network

import android.content.Context
import com.arny.videoplayer.BuildConfig
import com.readystatesoftware.chuck.ChuckInterceptor
import dagger.Binds
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
abstract class NetworkModule {

    @Binds
    abstract fun bindsResponseBodyConverter(converter: ResponseBodyConverterImpl): ResponseBodyConverter

    companion object {
        const val VIDEO_BASE_URL = "http://al.lordfilms-s.pw/"
//        const val VIDEO_BASE_URL = "https://my.lordfilms.to/"

        @Provides
        @Singleton
        fun provideRetrofit(client: OkHttpClient): Retrofit {
            return Retrofit.Builder()
                .baseUrl(VIDEO_BASE_URL)
                .client(client)
                .build()
        }

        @Provides
        @Singleton
        fun providesOkHttpClient(interceptor: Interceptor, context: Context): OkHttpClient {
            return OkHttpClient.Builder().writeTimeout(3, TimeUnit.MINUTES)
                .readTimeout(3, TimeUnit.MINUTES)
                .callTimeout(3, TimeUnit.MINUTES)
                .addInterceptor(interceptor)
                .addInterceptor(ChuckInterceptor(context))
                .addInterceptor(Interceptor { chain ->
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
                    chain.proceed(request)
                })
                .build()
        }


        @Provides
        @Singleton
        fun provideInterceptor(): Interceptor = if (BuildConfig.DEBUG)
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
}