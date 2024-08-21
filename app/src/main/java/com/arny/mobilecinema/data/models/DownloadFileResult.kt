package com.arny.mobilecinema.data.models

import java.io.File

data class DownloadFileResult(
    val file: File? = null,
    val progress: Int = 0,
    val size: Long? = null,
    val total: Long? = null,
    val error: String? = null
)