package com.arny.mobilecinema.data.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.log10
import kotlin.math.pow

fun File.isFileExists(): Boolean = isFile && exists()

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
 * Zip file
 * @param sourceFile - full path with extention etc. filepath/filename.txt
 * @param outputZipPath - full path with extention etc. filepath/compressed.zip
 */
fun zipFile(
    sourceFile: String,
    outputZipPath: String
) {
    val fos = FileOutputStream(outputZipPath)
    val zipOut = ZipOutputStream(fos)
    val fileToZip = File(sourceFile)
    val fis = FileInputStream(fileToZip)
    val zipEntry = ZipEntry(fileToZip.name)
    zipOut.putNextEntry(zipEntry)
    fis.copyTo(zipOut)
    zipOut.close()
    fis.close()
    fos.close()
}

/**
 * @param zipFilePath
 * @param destDirectory
 * @throws IOException
 */
@Throws(IOException::class)
fun unzip(zipFilePath: File, destDirectory: String) {
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
