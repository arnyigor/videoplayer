package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.di.models.VideoApiService

interface IVideoSourceFactory {
    fun createSource(
        hostStore: IHostStore,
        videoApiService: VideoApiService,
        responseBodyConverter: ResponseBodyConverter
    ): IVideoSource
}