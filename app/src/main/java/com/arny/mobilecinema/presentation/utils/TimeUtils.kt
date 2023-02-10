package com.arny.mobilecinema.presentation.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.concurrent.TimeUnit

fun secToMs(duration: Long): Long = TimeUnit.SECONDS.toMillis(duration)

fun String?.getTime(pattern: String): DateTime =
    DateTimeFormat.forPattern(pattern).parseDateTime(this)

fun DateTime.printTime(pattern: String = "YYYY.MM.dd HH:mm"): String =
    DateTimeFormat.forPattern(pattern).print(this)

fun getDuration(s: Int): String {
    var sec = s.toLong()
    val hours: Long = TimeUnit.SECONDS.toHours(sec)
    sec -= TimeUnit.HOURS.toSeconds(hours)
    val minutes: Long = TimeUnit.SECONDS.toMinutes(sec)
    sec -= TimeUnit.MINUTES.toSeconds(minutes)
    val seconds: Long = TimeUnit.SECONDS.toSeconds(sec)
    return StringBuilder(64).apply {
        if (hours != 0L) {
            append("%02d".format(hours))
            append(":")
        }
        if (minutes != 0L) {
            append("%02d".format(minutes))
            append(":")
        }
        append("%02d".format(seconds))
    }.toString()
}
