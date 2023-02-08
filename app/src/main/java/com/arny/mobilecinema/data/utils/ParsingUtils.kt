package com.arny.mobilecinema.data.utils

fun findByGroup(input: String, regex: Regex, group: Int): String? =
    regex.find(input)?.groupValues?.getOrNull(group)