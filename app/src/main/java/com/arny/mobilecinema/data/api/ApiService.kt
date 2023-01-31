package com.arny.mobilecinema.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpMethod
import io.ktor.util.InternalAPI
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
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
            println("A file saved to ${file.path} and has ${file.length()} bytes")
        }
    }
}