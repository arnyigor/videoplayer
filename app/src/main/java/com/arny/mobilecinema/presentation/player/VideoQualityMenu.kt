package com.arny.mobilecinema.presentation.player

/** Keep the original track reference: equal dimensions can belong to different track groups. */
internal fun <T> videoQualityMenu(qualities: List<Triple<Int, Int, T>>): List<Pair<String, T>> {
    val sorted = qualities.filter { it.first > 0 && it.second > 0 }
        .sortedWith(compareByDescending<Triple<Int, Int, T>> { it.second }.thenByDescending { it.first })
    val labels = sorted.map { "${it.second}p (${it.first} × ${it.second})" }
    val totals = labels.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return sorted.mapIndexed { index, quality ->
        val label = labels[index]
        val count = (seen[label] ?: 0) + 1
        seen[label] = count
        (if (totals.getValue(label) > 1) "$label ($count)" else label) to quality.third
    }
}
