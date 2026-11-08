@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks

internal data class TvTrackChoice(
    val label: String,
    val override: TrackSelectionOverride,
    val selected: Boolean,
)

/** Enumerate without changing the selector, and do not merge distinct tracks with equal languages. */
internal fun tvTrackChoices(tracks: Tracks, type: Int, unnamed: (Int) -> String): List<TvTrackChoice> {
    val result = mutableListOf<TvTrackChoice>()
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: unnamed(result.size + 1)
            result += TvTrackChoice(label, TrackSelectionOverride(group.mediaTrackGroup, listOf(index)), group.isTrackSelected(index))
        }
    }
    return result
}

/** Unlike the legacy selector extension, enumerating quality must not reset audio/subtitle preferences. */
internal fun tvVideoQualityChoices(tracks: Tracks): List<Pair<String, TrackSelectionOverride>> =
    tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
        (0 until group.length).mapNotNull { index ->
            val format = group.getTrackFormat(index)
            if (!group.isTrackSupported(index) || format.width <= 0 || format.height <= 0) null
            else Triple(format.width, format.height, TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
        }
    }.distinctBy { it.first to it.second }
        .sortedWith(compareByDescending<Triple<Int, Int, TrackSelectionOverride>> { it.first }.thenByDescending { it.second })
        .map { "${it.first} x ${it.second}" to it.third }

/** Only the audio override is touched; video quality and the global text-off rule stay intact. */
/** Equal languages are common (two Russian dubs); numbering keeps the dialog unambiguous. */
internal fun tvDistinctLabels(labels: List<String>): List<String> {
    val totals = labels.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return labels.map { label ->
        if (totals.getValue(label) == 1) label
        else "$label (${seen[label]?.plus(1).also { seen[label] = it ?: 1 } ?: 1})"
    }
}

internal fun tvAudioParameters(parameters: TrackSelectionParameters, choice: TvTrackChoice?): TrackSelectionParameters =
    parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .apply { if (choice != null) addOverride(choice.override) }.build()
