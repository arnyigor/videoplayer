package com.arny.mobilecinema.data.models

import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.Log
import com.antonkarpenko.ffmpegkit.Statistics

data class FfmpegResult(
    val result: String? = null,
    val cmd: String? = null,
    val log: Log? = null,
    val statistics: Statistics? = null,
    val session: FFmpegSession? = null
)