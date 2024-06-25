package com.arny.mobilecinema.data.api

import java.io.File

sealed class DownloadFileResult {
    data class Success(val file: File) : DownloadFileResult()

    data class Error(val message: String?, val cause: Throwable? = null) : DownloadFileResult()

    data class Progress(
        val progress: Int,
        val size: Long? = null,
        val total: Long? = null
    ) : DownloadFileResult()
}