@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import com.arny.mobilecinema.presentation.DeviceGuard

class TvTrackChoicesTest {
    @Before fun approvedDevice() { DeviceGuard.requireApprovedDevice() }
    private fun format(mime: String, language: String? = null) = Format.Builder()
        .setSampleMimeType(mime).setLanguage(language).build()
    private val audio = TrackGroup("audio", format(MimeTypes.AUDIO_AAC, "en"), format(MimeTypes.AUDIO_AAC, "en"))
    private val text = TrackGroup("text", format(MimeTypes.TEXT_VTT, "ru"))
    private val video = TrackGroup("video", format(MimeTypes.VIDEO_H264))
    private fun group(tracks: TrackGroup, supported: IntArray, selected: BooleanArray) = Tracks.Group(tracks, false, supported, selected)

    @Test fun sameLanguageTracksRemainDistinctAndPreserveSelection() {
        val choices = tvTrackChoices(Tracks(listOf(group(audio,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, true)))), C.TRACK_TYPE_AUDIO) { "Track $it" }
        assertEquals(2, choices.size)
        assertEquals(listOf("en", "en"), choices.map { it.label })
        assertFalse(choices[0].selected)
        assertTrue(choices[1].selected)
        assertEquals(listOf(1), choices[1].override.trackIndices)
    }

    @Test fun unsupportedTracksAndOtherTypesAreNotSelectable() {
        val choices = tvTrackChoices(Tracks(listOf(
            group(audio, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(true, false)),
            group(text, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)),
        )), C.TRACK_TYPE_AUDIO) { "Track $it" }
        assertEquals(1, choices.size)
        assertEquals(listOf(0), choices.single().override.trackIndices)
    }

    @Test fun duplicateLanguagesGetDistinguishableLabels() {
        assertEquals(listOf("en (1)", "en (2)", "de"), tvDistinctLabels(listOf("en", "en", "de")))
    }

    @Test fun unnamedTrackGetsAUsableLabel() {
        val unnamed = TrackGroup("unknown", format(MimeTypes.AUDIO_AAC))
        val choice = tvTrackChoices(Tracks(listOf(group(unnamed, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)))), C.TRACK_TYPE_AUDIO) { "Track $it" }
        assertEquals("Track 1", choice.single().label)
    }

    @Test fun videoQualityIsNumericAndDoesNotMergeDifferentResolutions() {
        val videoFormats = TrackGroup("qualities",
            Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(848).setHeight(480).build(),
            Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1920).setHeight(1080).build(),
            Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1280).setHeight(720).build())
        val choices = tvVideoQualityChoices(Tracks(listOf(group(videoFormats,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, true, false)))))
        assertEquals(listOf("1080p (1920 × 1080)", "720p (1280 × 720)", "480p (848 × 480)"), choices.map { it.first })
        assertEquals(listOf(1), choices.first().second.trackIndices)
    }

    @Test fun defaultAudioClearsOnlyItsOwnOverride() {
        val parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .addOverride(TrackSelectionOverride(audio, listOf(1)))
            .addOverride(TrackSelectionOverride(video, listOf(0)))
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        val result = tvAudioParameters(parameters, null)
        assertFalse(result.overrides.containsKey(audio))
        assertTrue(result.overrides.containsKey(video))
        assertTrue(result.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }
}
