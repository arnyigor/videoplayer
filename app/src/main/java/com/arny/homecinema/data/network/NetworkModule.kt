package com.arny.homecinema.data.network

import android.content.Context
import com.arny.homecinema.BuildConfig
import com.arny.homecinema.di.models.VideoApiService
import com.readystatesoftware.chuck.ChuckInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton


@Module
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindsResponseBodyConverter(converter: ResponseBodyConverterImpl): ResponseBodyConverter

    @Binds
    @Singleton
    abstract fun bindsHostStore(hostStore: HostStore): IHostStore

    companion object {
        const val VIDEO_BASE_URL = "http://al.lordfilms-s.pw/"

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
        fun providesOkHttpClient(
            context: Context,
            @Named("debugInterceptor") debugInterceptor: Interceptor,
            @Named("headersInterceptor") headersInterceptor: Interceptor,
            @Named("hostInterceptor") hostInterceptor: Interceptor
        ): OkHttpClient {
            return OkHttpClient.Builder().writeTimeout(3, TimeUnit.MINUTES)
                .readTimeout(3, TimeUnit.MINUTES)
                .callTimeout(3, TimeUnit.MINUTES)
                .addInterceptor(ChuckInterceptor(context))
                .addInterceptor(hostInterceptor)
                .addInterceptor(debugInterceptor)
                .addInterceptor(headersInterceptor)
                .cache(null)
                .build()
        }

        @Provides
        @Named("headersInterceptor")
        @Singleton
        fun provideHeadersInterceptor(): Interceptor = HeadersInterceptor()

        @Provides
        @Singleton
        @Named("hostInterceptor")
        fun provideHostInterceptor(hostStore: IHostStore): Interceptor =
            HostSelectorInterceptor(hostStore)

        @Provides
        @Named("debugInterceptor")
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