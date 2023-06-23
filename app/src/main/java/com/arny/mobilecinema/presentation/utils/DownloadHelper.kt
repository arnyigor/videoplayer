package com.arny.mobilecinema.presentation.utils

import java.util.concurrent.TimeUnit

class DownloadHelper {
    private var startTime = 0L

    fun reset() {
        startTime = System.currentTimeMillis()
    }

    fun getRemainTime(percent: Double): String {
        val dayInMs = TimeUnit.DAYS.toMillis(1)
        val elapsedTime = System.currentTimeMillis() - startTime
        val averageTime: Long = (elapsedTime.toDouble() / percent).toLong()
        val timeEstimated: Long = (averageTime * 100.0).toLong()
        var timeRemaining = timeEstimated - elapsedTime
        if (timeRemaining <= 0) {
            timeRemaining = 0
        }
        if (timeRemaining >= dayInMs) {
            timeRemaining = dayInMs
        }
        return getDurationBreakdown(timeRemaining)
    }

    private fun getDurationBreakdown(ms: Long): String {
        var millis = ms
        val days: Long = TimeUnit.MILLISECONDS.toDays(millis)
        millis -= TimeUnit.DAYS.toMillis(days)
        val hours: Long = TimeUnit.MILLISECONDS.toHours(millis)
        millis -= TimeUnit.HOURS.toMillis(hours)
        val minutes: Long = TimeUnit.MILLISECONDS.toMinutes(millis)
        millis -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds: Long = TimeUnit.MILLISECONDS.toSeconds(millis)
        millis -= TimeUnit.SECONDS.toMillis(seconds)
//        val mls: Long = TimeUnit.MILLISECONDS.toMillis(millis)
        return StringBuilder(64).apply {
            if (days != 0L) {
                append(days)
                append("д.")
            }
            if (hours != 0L) {
                append(hours)
                append("ч.")
            }
            if (minutes != 0L) {
                append(minutes)
                append("мин.")
            }
            append(seconds)
            append("сек.")
        }.toString()
    }
}