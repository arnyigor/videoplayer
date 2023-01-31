package com.arny.mobilecinema.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.util.zip.ZipFile
import kotlin.math.log10
import kotlin.math.pow

fun File.isFileExists(): Boolean = isFile && exists()

suspend fun File.create() {
    if (!this.isFileExists()) {
        check(withContext(Dispatchers.IO) { createNewFile() })
    }
}

fun formatFileSize(size: Long, digits: Int = 3): String {
    if (size <= 0) return "0"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    val digs = StringBuilder()
    for (i in 0 until digits) {
        digs.append("#")
    }
    return (DecimalFormat("#,##0.$digs").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups])
}

/**
 * @param zipFilePath
 * @param destDirectory
 * @throws IOException
 */
@Throws(IOException::class)
fun File.unzip(destDirectory: String) {
    val zipFilePath = this
    File(destDirectory).run {
        if (!exists()) {
            mkdirs()
        }
    }
    ZipFile(zipFilePath).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val filePath = destDirectory + File.separator + entry.name
                if (!entry.isDirectory) {
                    // if the entry is a file, extracts it
                    FileOutputStream(filePath).use { input.copyTo(it) }
                } else {
                    // if the entry is a directory, make the directory
                    val dir = File(filePath)
                    dir.mkdir()
                }
            }
        }
    }
}
