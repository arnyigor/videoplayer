package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.di.models.VideoApiService
import javax.inject.Inject

class VideoSourceFactory @Inject constructor() : IVideoSourceFactory {
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
            else -> AlLordFilmVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
        }
    }
}