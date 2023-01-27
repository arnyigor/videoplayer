package com.arny.mobilecinema.data.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.util.zip.ZipEntry
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
 * UnzipFile
 * @param fileZip - full path with extention etc. filepath/filename.txt
 * @param destDirPath - destination folder path
 */
fun unzipFile(
    fileZip: String,
    destDirPath: String
) {
    val destDir = File(destDirPath)
    val zis = ZipInputStream(FileInputStream(fileZip))
    var zipEntry: ZipEntry? = zis.nextEntry
    while (zipEntry != null) {
        val newFile: File = newFile(destDir, zipEntry)
        if (zipEntry.isDirectory) {
            if (!newFile.isDirectory && !newFile.mkdirs()) {
                throw IOException("Failed to create directory $newFile")
            }
        } else {
            val parent = newFile.parentFile
            if (parent != null) {
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory $parent")
                }
            }
            val fos = FileOutputStream(newFile)
            zis.copyTo(fos)
            fos.close()
        }
        zipEntry = zis.nextEntry
    }
    zis.closeEntry()
    zis.close()
}

@Throws(IOException::class)
fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
    val destFile = File(destinationDir, zipEntry.name)
    val destDirPath = destinationDir.canonicalPath
    val destFilePath = destFile.canonicalPath
    if (!destFilePath.startsWith(destDirPath + File.separator)) {
        throw IOException("Entry is outside of the target dir: " + zipEntry.name)
    }
    return destFile
}
