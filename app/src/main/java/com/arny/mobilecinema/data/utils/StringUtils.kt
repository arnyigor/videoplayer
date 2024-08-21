package data.utils

fun String.isNumeric(): Boolean = this.all { char -> char.isDigit() }