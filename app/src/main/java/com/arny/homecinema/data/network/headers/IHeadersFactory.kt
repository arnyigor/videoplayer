package com.arny.homecinema.data.network.headers

import com.arny.homecinema.data.network.IHostStore

interface IHeadersFactory {
    fun createHeaders(hostStore: IHostStore): IRequestHeaders
}