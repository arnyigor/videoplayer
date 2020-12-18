package com.arny.homecinema.data.network

import javax.inject.Inject

class HostStore @Inject constructor() : IHostStore {
    override var host: String? = null
}