package com.arny.mobilecinema.presentation.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

fun <T> BufferedSharedFlow(
    bufferCapacity: Int = 1,
    bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    replay: Int = 0,
): MutableSharedFlow<T> = MutableSharedFlow(
    extraBufferCapacity = bufferCapacity,
    onBufferOverflow = bufferOverflow,
    replay = replay
)