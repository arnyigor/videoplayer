@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.player

import android.content.Context
import com.arny.mobilecinema.domain.models.Movie
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo
import androidx.media3.common.TrackSelectionOverride

fun Movie.getCinemaUrl(): String = getAllCinemaUrls().firstOrNull().orEmpty()

fun Movie.getAllCinemaUrls(): List<String> {
    val hdUrls = cinemaUrlData?.hdUrl?.urls.orEmpty()
    val cinemaUrls = cinemaUrlData?.cinemaUrl?.urls.orEmpty()
    return (hdUrls + cinemaUrls).filter { it.isNotBlank() }.distinct()
}

fun DefaultTrackSelector.generateLanguagesList(context: Context): List<Pair<String, TrackSelectionOverride>> {
    val trackOverrideList = ArrayList<Pair<String, TrackSelectionOverride>>()
    val renderTrack = this.currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0
    for (rendererIndex in 0 until renderCount) {
        if (isSupportedFormat(renderTrack, rendererIndex)) {
            val renderType = renderTrack?.getRendererType(rendererIndex)
            val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
            val trackGroupsCount = trackGroups?.length!!
            if (renderType == C.TRACK_TYPE_AUDIO) {
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
                            val override = TrackSelectionOverride(track, listOf(trackIndex))
                            if (trackName.isNotBlank()) {
                                trackOverrideList.add(Pair(trackName, override))
                            }
                        }
                    }
                }
            }
        }
    }
    return trackOverrideList.sortedByDescending { it.first }
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
                            val format = track.getFormat(trackIndex)
                            val trackName = "${format.width} x ${format.height}"
                            if (format.selectionFlags == C.SELECTION_FLAG_AUTOSELECT) {
                                trackName.plus(" (Default)")
                            }
                            val override = TrackSelectionOverride(track, listOf(trackIndex))
                            if (trackName.isNotBlank()) {
                                trackOverrideList.add(Pair(trackName, override))
                            }
                        }
                    }
                }
            }
        }
    }
    // Разрешение сравниваем численно: при строковой сортировке «848 x 480»
    // оказывается «выше» «1920 x 1080», и выбиралось не максимальное качество.
    return trackOverrideList.distinctBy { it.first }.sortedWith(
        compareByDescending<Pair<String, TrackSelectionOverride>> {
            it.first.substringBefore(" x ").toIntOrNull() ?: 0
        }.thenByDescending {
            it.first.substringAfter(" x ", "0").toIntOrNull() ?: 0
        }
    )
}

fun DefaultTrackSelector.generateSubTitlesList(context: Context): List<Pair<String, TrackSelectionOverride>> {
    val trackOverrideList = ArrayList<Pair<String, TrackSelectionOverride>>()
    val renderTrack = this.currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0
    for (rendererIndex in 0 until renderCount) {
        if (isSupportedFormat(renderTrack, rendererIndex)) {
            val renderType = renderTrack?.getRendererType(rendererIndex)
            val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
            val trackGroupsCount = trackGroups?.length!!
            if (renderType == C.TRACK_TYPE_TEXT) {
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
                            val format = track.getFormat(trackIndex)
                            // С Media3 1.4 субтитры из Matroska отдаются как
                            // application/x-media3-cues: их отрисовывает сам Media3, поэтому
                            // SubtitleDecoderFactory их не знает и возвращает false. Без этой
                            // ветки список субтитров был пустым, хотя дорожка есть.
                            val subtitleDecoderFactory = SubtitleDecoderFactory.DEFAULT
                            val supportsFormat =
                                format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES ||
                                    subtitleDecoderFactory.supportsFormat(format)
                            var trackName = format.language
                            if (supportsFormat) {
                                if (trackName.isNullOrBlank()) {
                                    trackName = "Субтитры ${trackIndex + 1}"
                                }
                                val override = TrackSelectionOverride(track, listOf(trackIndex))
                                if (trackName.isNotBlank()) {
                                    trackOverrideList.add(Pair(trackName, override))
                                }
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