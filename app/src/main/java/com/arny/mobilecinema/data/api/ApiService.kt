package com.arny.mobilecinema.data.api

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.DownloadFileResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.util.InternalAPI
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException

class ApiService(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
    @OptIn(InternalAPI::class)
    suspend fun downloadFile(file: File, url: String, timeoutMillis: Long? = null) {
        httpClient.prepareGet(url) {
            method = HttpMethod.Get
            header("User-Agent", BROWSER_USER_AGENT)
            header("Accept", "application/octet-stream,application/zip,*/*")
            header("Accept-Encoding", "identity")
            timeoutMillis?.let { requestTimeout ->
                timeout {
                    connectTimeoutMillis = requestTimeout
                    requestTimeoutMillis = requestTimeout
                    socketTimeoutMillis = requestTimeout
                }
            }
        }.execute { httpResponse ->
            if (!httpResponse.status.isSuccess()) {
                file.delete()
                throw IOException("HTTP ${httpResponse.status.value}: ${httpResponse.status.description}")
            }
            httpResponse.content.copyAndClose(file.writeChannel())
        }
    }

    fun downloadFileWithProgress(
        file: File,
        url: String
    ): Flow<DataResultWithProgress<DownloadFileResult>> {
        return flow<DataResultWithProgress<DownloadFileResult>> {
            var complete = false
            try {
                httpClient.prepareGet(url) {
                    header("User-Agent", BROWSER_USER_AGENT)
                    header("Accept", "application/octet-stream,application/zip,*/*")
                    header("Accept-Encoding", "identity")
                    timeout {
                        connectTimeoutMillis = 60000 * 60
                        requestTimeoutMillis = 60000 * 60
                        socketTimeoutMillis = 60000 * 60
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("HTTP ${response.status.value}: ${response.status.description}")
                    }
                    val channel: ByteReadChannel = response.body()
                    val total = response.contentLength()?.takeIf { it > 0 }
                    var downloaded = 0L
                    var reportedBytes = 0L
                    var lastPercent = -1
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = channel.readAvailable(buffer, 0, buffer.size)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            downloaded += count
                            // Reserve 100% until EOF, size validation and closing the file.
                            val percent = if (total != null) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0
                            if ((total != null && percent != lastPercent) ||
                                (total == null && downloaded - reportedBytes >= 256 * 1024)) {
                                emit(DataResultWithProgress.Progress(DownloadFileResult(
                                    progress = percent, size = downloaded, total = total,
                                )))
                                lastPercent = percent
                                reportedBytes = downloaded
                            }
                        }
                    }
                    if (downloaded == 0L) throw DataThrowable(R.string.download_error_file_is_empty)
                    if (total != null && downloaded != total) throw IOException("Incomplete download: $downloaded / $total bytes")
                    complete = true
                    emit(DataResultWithProgress.Progress(DownloadFileResult(
                        progress = if (total != null) 100 else 0, size = downloaded, total = total,
                    )))
                    emit(DataResultWithProgress.Success(DownloadFileResult(file = file, size = downloaded, total = total)))
                }
            } finally {
                // Interrupted attempts are not resumable: never leave an incomplete archive as a valid file.
                if (!complete) file.delete()
            }
        }.catch { cause ->
            emit(DataResultWithProgress.Error(cause))
        }.flowOn(Dispatchers.IO)
    }

    suspend fun checkPath(url: String, timeoutMillis: Long? = null): HttpStatusCode =
        httpClient.request(url) {
            method = HttpMethod.Get
            timeoutMillis?.let { requestTimeout ->
                timeout {
                    connectTimeoutMillis = requestTimeout
                    requestTimeoutMillis = requestTimeout
                    socketTimeoutMillis = requestTimeout
                }
            }
        }.status
}