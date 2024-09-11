package com.arny.mobilecinema.data.utils

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.pow

fun Long.printTime(pattern: String = "YYYY.MM.dd HH:mm"): String {
    val zone = DateTimeZone.forID("Europe/Moscow")
    return DateTime(this, zone).printTime(pattern)
}

fun loadingText(
    index: Int,
    size: Int,
    progress: Double,
    duration: Long,
    durationGlobal: Long,
    remainStr: String,
): String = "Готово $index из $size=${progress.format(2)}%" +
        " время операции ${getDurationBreakdown(duration)}, общее время ${getDurationBreakdown(durationGlobal)} примерное время \n$remainStr"

fun noticeText(
    index: Int,
    size: Int,
    progress: Double,
    remainStr: String,
): String = "$index из $size" +
        "\n${progress.format(2)}%" +
        "\n~ $remainStr"

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun getProgressPersent(
    i: Int,
    size: Int,
): Double = (i.toDouble() / size.toDouble()) * 100.0

fun getProgress(
    i: Int,
    size: Int,
): Double = i.toDouble() / size.toDouble()


fun getTime(pattern: String, time: String): DateTime {
    return DateTimeFormat.forPattern(pattern).parseDateTime(time)
}

fun DateTime.printTime(pattern: String): String {
    return DateTimeFormat.forPattern(pattern).print(this)
}

fun getDurationTimeSec(durationText: String): Int {
    val durList = durationText.split(":").map { it.trimIndent() }
    val h = durList[0].toInt()
    val m = durList[1].toInt()
    val s = durList[2].toInt()
    return h * 3600 + m * 60 + s
}

fun getRemain(
    i: Int,
    size: Int,
    timeStart: Long,
    average: MutableList<Long>? = null,
    maxAverageSize: Int = 0
): String {
    val duration = getJsoupDuration(timeStart)
    var remain = duration * (size - i)
    if (average != null && maxAverageSize != 0 && average.size > maxAverageSize) {
        average.removeAt(0)
    }
    if (average != null) {
        average.add(duration)
        remain = average.average().toLong() * (size - i)
    }
    return getDurationBreakdown(remain)
}

fun getJsoupDuration(timeStart: Long): Long = System.currentTimeMillis() - timeStart

fun getDurationBreakdown(ms: Long): String {
    var millis = ms
    val days: Long = TimeUnit.MILLISECONDS.toDays(millis)
    millis -= TimeUnit.DAYS.toMillis(days)
    val hours: Long = TimeUnit.MILLISECONDS.toHours(millis)
    millis -= TimeUnit.HOURS.toMillis(hours)
    val minutes: Long = TimeUnit.MILLISECONDS.toMinutes(millis)
    millis -= TimeUnit.MINUTES.toMillis(minutes)
    val seconds: Long = TimeUnit.MILLISECONDS.toSeconds(millis)
    millis -= TimeUnit.SECONDS.toMillis(seconds)
    val mls: Long = TimeUnit.MILLISECONDS.toMillis(millis)
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
        append(",")
        append(mls)
        append("сек.")
    }.toString()
}

fun getElapsed(elapsed: Double, precision: Int): String {
    if (elapsed <= 0) return "0"
    val units = arrayOf("нс", "мкс", "мс", "сек")
    val digitGroups = (log10(elapsed) / log10(1000.0)).toInt()
    val digs = StringBuilder()
    for (i in 0 until precision) {
        digs.append("#")
    }
    return (DecimalFormat("#,##0.$digs").format(
        elapsed / (1000.0).pow(digitGroups.toDouble())
    ) + " " + units[digitGroups])
}