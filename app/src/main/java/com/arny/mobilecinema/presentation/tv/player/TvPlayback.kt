@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaSource
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.playback.PlayableRef
import com.arny.mobilecinema.domain.playback.legacyPlayableRef
import com.arny.mobilecinema.domain.playback.playableRefs

internal data class TvPlaybackTarget(val ref: PlayableRef?, val positionMs: Long)

/** An explicit episode includes the first episode too: (0, 0) is not a resume sentinel. */
internal fun resolveTvPlaybackTarget(
    movie: Movie,
    requestedPlayableId: String,
    savedSeason: Int,
    savedEpisode: Int,
    savedPositionMs: Long,
): TvPlaybackTarget {
    val saved = movie.legacyPlayableRef(savedSeason, savedEpisode)
    val requested = movie.playableRefs().firstOrNull { it.id.value == requestedPlayableId }
    val ref = requested ?: saved ?: movie.playableRefs().firstOrNull()
    val position = if (requested == null || requested.id == saved?.id) savedPositionMs else 0L
    return TvPlaybackTarget(ref, position.coerceAtLeast(0L))
}

/** Let TV UI offer the next episode instead of silently advancing the playlist. */
internal fun ExoPlayer.configureTvPlayback() {
    setPauseAtEndOfMediaItems(true)
    // Closest-sync seeking can repeatedly land on the same old keyframe instead of advancing 10 s.
    setSeekParameters(SeekParameters.EXACT)
}

/** Apply the start position when setting the source, before prepare can reset it. */
internal fun ExoPlayer.prepareTvSource(source: MediaSource, positionMs: Long = 0L) {
    setMediaSource(source, positionMs.coerceAtLeast(0L))
    prepare()
    playWhenReady = true
}
