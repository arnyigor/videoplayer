package com.arny.mobilecinema.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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
        }
    }

    suspend fun checkPath(url: String): HttpStatusCode =
        httpClient.request(url) {
            method = HttpMethod.Get
        }.status
}