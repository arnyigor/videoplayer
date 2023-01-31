package com.arny.mobilecinema.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.gson.gson
import javax.inject.Inject

class KtorClient @Inject constructor() {
    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        // Timeout
        install(HttpTimeout) {
            requestTimeoutMillis = 120000L
            connectTimeoutMillis = 120000L
            socketTimeoutMillis = 120000L
        }
    }
}