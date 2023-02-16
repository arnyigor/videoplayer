package com.arny.mobilecinema.data.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.File
import javax.inject.Inject

class ApiService @Inject constructor(
    private val httpClient: HttpClient
) {
    @OptIn(InternalAPI::class)
    suspend fun downloadFile(file: File, url: String) {
        httpClient.prepareGet(url) {
            method = HttpMethod.Get
        }.execute { httpResponse ->
            httpResponse.content.copyAndClose(file.writeChannel())
        }
    }
}