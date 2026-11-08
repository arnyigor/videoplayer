@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/** Session controllers must obey the same pending-episode rules as foreground remote keys. */
internal class TvSessionPlayer(
    player: Player,
    private val onPause: () -> Unit,
    private val onPlayPending: () -> Boolean,
) : ForwardingPlayer(player) {
    override fun pause() {
        onPause()
        super.pause()
    }

    override fun stop() = pause()

    override fun play() {
        if (!onPlayPending()) super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun seekToNextMediaItem() {
        if (!onPlayPending()) super.seekToNextMediaItem()
    }

    override fun seekToNext() {
        if (!onPlayPending()) super.seekToNext()
    }
}
