package com.arny.mobilecinema.data.api

import com.arny.mobilecinema.data.utils.isFileExists
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
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ApiService @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun downloadFile(file: File, url: String) {
        httpClient.prepareGet(url) {
            headers {
                append("Content-Type", "text/plain;charset=UTF-8")
            }
        }.execute { httpResponse ->
            if (!file.isFileExists()) {
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
                    Timber.d("Received ${file.length()} bytes from ${httpResponse.contentLength()}")
                }
            }
            val message = "A file saved to ${file.path} and has ${file.length()} bytes"
            Timber.d(message)
        }
    }
}