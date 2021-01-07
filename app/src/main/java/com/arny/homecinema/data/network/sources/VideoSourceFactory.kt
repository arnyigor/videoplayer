package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.network.hosts.HostStoreImpl
import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.network.response.ResponseBodyConverter
import com.arny.homecinema.data.repository.sources.assets.AssetsReader
import com.arny.homecinema.di.models.VideoApiService
import javax.inject.Inject

class VideoSourceFactory @Inject constructor(
    private val assetsReader: AssetsReader
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
            HostStoreImpl.LORDFILM_ADA_HOST-> LordFilmAdaVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.LORDFILM_HD_HOST-> LordFilmHDVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.HOST_MOCK -> MockDataVideoSource(
                hostStore,
                assetsReader
            )
            HostStoreImpl.HOST_MOCK2 -> MockDataVideoSource(
                hostStore,
                assetsReader
            )
            else -> AlLordFilmVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
        }
    }
}