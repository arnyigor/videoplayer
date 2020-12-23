package com.arny.homecinema.data.network.sources

import android.content.Context
import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.di.models.VideoApiService
import javax.inject.Inject

class VideoSourceFactory @Inject constructor(
    private val context: Context
) : IVideoSourceFactory {
    override fun createSource(
        hostStore: IHostStore,
        videoApiService: VideoApiService,
        responseBodyConverter: ResponseBodyConverter
    ): IVideoSource {
        return when (hostStore.host) {
            HostStoreImpl.LORDFILM_AL_HOST -> AlLordFilmVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.LORDFILM_KINO_I_HOST -> KinoIOnlineVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.LORDFILM_23s_HOST -> Lord23sFilmVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK -> MockDataVideoSource(
                hostStore,
                context
            )
            HostStoreImpl.LORDFILM_KINO_I_HOST_MOCK2 -> MockDataVideoSource(
                hostStore,
                context
            )
            else -> AlLordFilmVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
        }
    }
}