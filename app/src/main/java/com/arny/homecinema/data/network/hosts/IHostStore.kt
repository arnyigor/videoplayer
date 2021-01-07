package com.arny.homecinema.data.network.hosts

interface IHostStore {
    fun saveHost(source: String)
    var savedHost: String?
    val baseUrls: List<String>
    var host: String?
    val baseUrl: String
    val mainPageHeaders: Map<String, String?>
    val baseHeaders: Map<String, String>
    val availableHosts: List<String>
}
