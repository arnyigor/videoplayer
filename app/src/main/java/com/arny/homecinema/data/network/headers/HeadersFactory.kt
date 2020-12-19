package com.arny.homecinema.data.network.headers

import com.arny.homecinema.data.network.HostStore
import com.arny.homecinema.data.network.IHostStore
import javax.inject.Inject

class HeadersFactory @Inject constructor() : IHeadersFactory {
    override fun createHeaders(hostStore: IHostStore): IRequestHeaders {
        return when (hostStore.host) {
            HostStore.LORDFILM_AL_HOST -> AlRequestHeaders(hostStore)
            HostStore.LORDFILM_20_ZONE_HOST -> Lord14RequestHeaders()
            HostStore.LORDFILM_19DEC_HOST -> Lord19DecRequestHeaders(hostStore)
            else -> AlRequestHeaders(hostStore)
        }
    }
}