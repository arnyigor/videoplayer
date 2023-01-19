package com.arny.mobilecinema.data.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.gson.gson

class KtorClient private constructor() {
    companion object {
        fun getInstance() = KtorClient()
    }

    val client = HttpClient(OkHttp) {
        install(DefaultRequest) {
            headers.append("Content-Type", "application/json")
        }
        install(ContentNegotiation) {
            gson()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        // Timeout
        install(HttpTimeout) {
            requestTimeoutMillis = 15000L
            connectTimeoutMillis = 15000L
            socketTimeoutMillis = 15000L
        }
    }
}