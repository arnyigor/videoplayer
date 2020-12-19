package com.arny.homecinema.data.network

interface IHostStore {
    var host: String?
    val baseUrl: String
    val mainPageHeaders: Map<String, String?>?
    val baseHeaders: Map<String, String>
}
