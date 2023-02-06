package com.arny.mobilecinema.presentation.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.concurrent.TimeUnit

fun secToMs(duration: Long): Long = TimeUnit.SECONDS.toMillis(duration)

fun String?.getTime(pattern: String): DateTime =
    DateTimeFormat.forPattern(pattern).parseDateTime(this)

fun DateTime.printTime(pattern: String): String = DateTimeFormat.forPattern(pattern).print(this)
