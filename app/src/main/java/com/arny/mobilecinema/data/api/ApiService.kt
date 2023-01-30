package com.arny.mobilecinema.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ApiService @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun downloadFile(newFilePath: String, url: String): File {
        return httpClient.prepareGet(url) {
            headers {
                append("Content-Type", "text/plain;charset=UTF-8")
            }
        }.execute { httpResponse ->
            val file = File(newFilePath)
            if (!file.exists()) {
                val newFile = withContext(Dispatchers.IO) {
                    file.createNewFile()
                }
                check(newFile)
            }
            val channel: ByteReadChannel = httpResponse.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    file.appendBytes(bytes)
                    println("Received ${file.length()} bytes from ${httpResponse.contentLength()}")
                }
            }
            val message = "A file saved to ${file.path} and has ${file.length()} bytes"
            println(message)
            file
        }
    }

}