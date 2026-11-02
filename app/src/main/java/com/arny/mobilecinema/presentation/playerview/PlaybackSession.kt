package com.arny.mobilecinema.presentation.playerview

/** Main-thread session ownership, independent of the engine used by the screen. */
internal class PlaybackSession<T : Any>(private val releaseEngine: (T) -> Unit) {
    var player: T? = null
        private set

    fun acquire(create: () -> T): T = player ?: create().also { player = it }

    fun release() {
        val current = player ?: return
        player = null
        releaseEngine(current)
    }
}
