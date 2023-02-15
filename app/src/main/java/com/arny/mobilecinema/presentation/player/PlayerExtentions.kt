package com.arny.mobilecinema.presentation.player

import android.content.Context
import com.arny.mobilecinema.domain.models.Movie
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters

fun Movie.getCinemaUrl(): String {
    val hdUrl = cinemaUrlData?.hdUrl?.urls?.firstOrNull().orEmpty()
    val cinemaUrl = cinemaUrlData?.cinemaUrl?.urls?.firstOrNull().orEmpty()
    return when {
        hdUrl.isNotBlank() -> hdUrl
        cinemaUrl.isNotBlank() -> cinemaUrl
        else -> ""
    }
}

fun DefaultTrackSelector.generateLanguagesList(context: Context): List<Pair<String, TrackSelectionOverride>> {
    val trackOverrideList = ArrayList<Pair<String, TrackSelectionOverride>>()
    val renderTrack = this.currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0
    for (rendererIndex in 0 until renderCount) {
        if (isSupportedFormat(renderTrack, rendererIndex)) {
            val trackGroupType = renderTrack?.getRendererType(rendererIndex)
            val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
            val trackGroupsCount = trackGroups?.length!!
            if (trackGroupType == C.TRACK_TYPE_AUDIO) {
                for (groupIndex in 0 until trackGroupsCount) {
                    val trackCount = trackGroups[groupIndex].length
                    for (trackIndex in 0 until trackCount) {
                        val isTrackSupported = renderTrack.getTrackSupport(
                            rendererIndex,
                            groupIndex,
                            trackIndex
                        ) == C.FORMAT_HANDLED
                        if (isTrackSupported) {
                            val track = trackGroups[groupIndex]
                            val trackName = track.getFormat(trackIndex).language.orEmpty()
                            if (track.getFormat(trackIndex).selectionFlags == C.SELECTION_FLAG_AUTOSELECT) {
                                trackName.plus(" (Default)")
                            }
                            val builder = TrackSelectionParameters.Builder(context).clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            val override = TrackSelectionOverride(track, listOf(trackIndex)).apply {
                                setParameters(builder.build())
                            }
                            if (trackName.isNotBlank()) {
                                trackOverrideList.add(Pair(trackName, override))
                            }
                        }
                    }
                }
            }
        }
    }
    return trackOverrideList.distinctBy { it.first }.sortedByDescending { it.first }
}

fun DefaultTrackSelector.generateQualityList(context: Context): List<Pair<String, TrackSelectionOverride>> {
    val trackOverrideList = ArrayList<Pair<String, TrackSelectionOverride>>()
    val renderTrack = this.currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0
    for (rendererIndex in 0 until renderCount) {
        if (isSupportedFormat(renderTrack, rendererIndex)) {
            val trackGroupType = renderTrack?.getRendererType(rendererIndex)
            val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
            val trackGroupsCount = trackGroups?.length!!
            if (trackGroupType == C.TRACK_TYPE_VIDEO) {
                for (groupIndex in 0 until trackGroupsCount) {
                    val videoQualityTrackCount = trackGroups[groupIndex].length
                    for (trackIndex in 0 until videoQualityTrackCount) {
                        val isTrackSupported = renderTrack.getTrackSupport(
                            rendererIndex,
                            groupIndex,
                            trackIndex
                        ) == C.FORMAT_HANDLED
                        if (isTrackSupported) {
                            val track = trackGroups[groupIndex]
                            val trackName =
                                "${track.getFormat(trackIndex).width} x ${track.getFormat(trackIndex).height}"
                            if (track.getFormat(trackIndex).selectionFlags == C.SELECTION_FLAG_AUTOSELECT) {
                                trackName.plus(" (Default)")
                            }
                            val builder = TrackSelectionParameters.Builder(context)
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            val override = TrackSelectionOverride(track, listOf(trackIndex)).apply {
                                setParameters(builder.build())
                            }
                            if (trackName.isNotBlank()) {
                                trackOverrideList.add(Pair(trackName, override))
                            }
                        }
                    }
                }
            }
        }
    }
    return trackOverrideList.distinctBy { it.first }.sortedByDescending { it.first }
}

fun isSupportedFormat(mappedTrackInfo: MappedTrackInfo?, rendererIndex: Int): Boolean =
    when (mappedTrackInfo?.getTrackGroups(rendererIndex)?.length) {
        0 -> false
        else -> {
            val rendererType = mappedTrackInfo?.getRendererType(rendererIndex)
            rendererType == C.TRACK_TYPE_VIDEO || rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_TEXT
        }
    }