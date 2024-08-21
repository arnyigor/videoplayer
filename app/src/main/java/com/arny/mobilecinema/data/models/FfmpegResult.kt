package com.arny.mobilecinema.data.models

import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.Statistics

data class FfmpegResult(
    val result: String? = null,
    val cmd: String? = null,
    val log: Log? = null,
    val statistics: Statistics? = null,
    val session: FFmpegSession? = null
)