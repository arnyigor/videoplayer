package com.arny.mobilecinema.data.utils

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
    return (DecimalFormat("#,##0.$digs").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units.getOrNull(
        digitGroups
    ).orEmpty())
}

fun Context.unzipData(zipFile: File, extension: String): List<File> {
    val path = filesDir.path
    zipFile.unzip(path)
    val files = File(path).listFiles()?.filter { it.name.endsWith(extension) }
    val checkAllFiles = files?.all {
        it.isFileExists() && it.length() > 0
    }
    return if (checkAllFiles == true) {
        zipFile.delete()
        files.toList()
    } else {
        emptyList()
    }
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

fun Context.saveFileToDownloadFolder(file: File, newName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file))
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver: ContentResolver = contentResolver
            val dstUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

            if (dstUri != null) {
                resolver.openFileDescriptor(dstUri, "w").use { fd ->
                    val stream = ParcelFileDescriptor.AutoCloseOutputStream(fd)
                    copyIntoFileStream(file.inputStream(), stream)
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(dstUri, values, null, null)
                true
            } else {
                false
            }
        } else {
            val destinationPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).path + File.separator + file.name
            val input: InputStream = FileInputStream(file)
            FileOutputStream(destinationPath).use { output ->
                copyIntoFileStream(input, output)
            }
            val downloadManager: DownloadManager =
                getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.addCompletedDownload(
                newName, newName, true,
                getMimeType(file), destinationPath, file.length(), true
            )
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun copyFile(
    out: FileOutputStream,
    inputStream: InputStream
) {
    val bufferedInputStream = BufferedInputStream(inputStream, DEFAULT_BUFFER_SIZE);
    val byteArr = ByteArray(DEFAULT_BUFFER_SIZE)
    var progress: Long = 0;
    var actual = 0;
    while (actual != -1) {
        out.write(byteArr, 0, actual);
        actual = bufferedInputStream.read(byteArr, 0, DEFAULT_BUFFER_SIZE);
        progress += actual;
    }
    out.close();
    bufferedInputStream.close();
    inputStream.close();
}


private fun copyIntoFileStream(inputStream: InputStream, outputStream: FileOutputStream) {
    inputStream.use { input ->
        outputStream.use { output ->
            input.copyTo(output, DEFAULT_BUFFER_SIZE)
        }
    }
}

fun getMimeType(file: File): String {
    var type: String? = null
    try {
        val fileName = file.name
        type = getMimeType(fileName)
    } catch (e: StringIndexOutOfBoundsException) {
        e.printStackTrace()
    }
    return type ?: "application"
}

fun getMimeType(fileName: String): String {
    val extension =
        MimeTypeMap.getFileExtensionFromUrl(fileName.substring(fileName.lastIndexOf(".")))
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return type ?: "application"
}


fun Context.getMimeType(uri: Uri): String? = contentResolver.getType(uri)