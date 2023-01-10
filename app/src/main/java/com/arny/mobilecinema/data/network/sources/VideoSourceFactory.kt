package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.HostStoreImpl
import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReader
import com.arny.mobilecinema.data.api.VideoApiService
import javax.inject.Inject

class VideoSourceFactory @Inject constructor(
    private val assetsReader: AssetsReader,
    private val wrapApiSource: WrapApiSource
) : IVideoSourceFactory {
    override fun createSource(
        hostStore: IHostStore,
        videoApiService: VideoApiService,
        responseBodyConverter: ResponseBodyConverter,
    ): IVideoSource {
        return when (hostStore.host) {
            HostStoreImpl.WRAP -> wrapApiSource
            HostStoreImpl.HOST_MOCK -> MockDataVideoSource(
                hostStore,
                assetsReader
            )
            else -> MockDataVideoSource(
                hostStore,
                assetsReader
            )
        }
    }
}