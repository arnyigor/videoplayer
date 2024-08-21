package com.arny.mobilecinema.data.api

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.DownloadFileResult
import com.arny.mobilecinema.data.utils.getFullError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
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
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    suspend fun downloadFileWithProgress(
        file: File,
        url: String
    ): Flow<DataResultWithProgress<DownloadFileResult>> {
        return callbackFlow {
            try {
                httpClient.prepareGet(url) {
                    timeout {
                        connectTimeoutMillis = 60000 * 60
                        requestTimeoutMillis = 60000 * 60
                        socketTimeoutMillis = 60000 * 60
                    }
                }
                    .execute { httpResponse ->
                        val channel: ByteReadChannel = httpResponse.body<ByteReadChannel>()
                        val contentLength = httpResponse.contentLength() ?: 0L
                        if (contentLength > 0) {
                            while (!channel.isClosedForRead) {
                                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                while (!packet.isEmpty) {
                                    val bytes = packet.readBytes()
                                    file.appendBytes(bytes)
                                    val length = file.length()
                                    val percent: Int =
                                        (length * 100 / contentLength).toInt()
                                    trySend(
                                        DataResultWithProgress.Progress(
                                            DownloadFileResult(
                                                progress = percent,
                                                size = length,
                                                total = contentLength
                                            )
                                        )
                                    )
                                }
                            }
                            if (httpResponse.status.isSuccess()) {
                                trySend(
                                    DataResultWithProgress.Success(
                                        DownloadFileResult(file = file)
                                    )
                                )
                            } else {
                                trySend(
                                    DataResultWithProgress.Error(
                                        Throwable(
                                            getFullError(
                                                httpResponse.body<Throwable>().fillInStackTrace()
                                            )
                                        )
                                    )
                                )
                            }
                        } else {
                            trySend(
                                DataResultWithProgress.Error(
                                    DataThrowable(R.string.download_error_file_is_empty)
                                )
                            )
                        }
                    }

            } catch (e: TimeoutCancellationException) {
                trySend(DataResultWithProgress.Error(Throwable(getFullError(e))))
            } catch (t: Throwable) {
                trySend(DataResultWithProgress.Error(Throwable(getFullError(t))))
            }
            awaitClose()
        }
    }

    suspend fun checkPath(url: String): HttpStatusCode =
        httpClient.request(url) {
            method = HttpMethod.Get
        }.status
}