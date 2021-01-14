package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.HostStoreImpl
import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReader
import com.arny.mobilecinema.di.models.VideoApiService
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
            HostStoreImpl.ALL_TABFILL_HOST-> AlTabFillVideoSource(
                hostStore,
                videoApiService,
                responseBodyConverter
            )
            HostStoreImpl.HOST_MOCK -> MockDataVideoSource(
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