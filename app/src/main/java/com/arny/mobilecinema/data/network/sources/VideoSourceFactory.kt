package com.arny.mobilecinema.data.network.sources

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
    ): IVideoSource =
        MockDataVideoSource(hostStore = hostStore, assetsReader = assetsReader)
}