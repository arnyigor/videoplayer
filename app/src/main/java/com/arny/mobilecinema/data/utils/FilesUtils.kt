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

fun Context.unzipData(zipFile: File, extension: String): List<File> =
    extractCatalogData(zipFile, filesDir, extension)

/** The caller owns the returned files and must delete their temporary directory after import. */
internal fun extractCatalogData(zipFile: File, filesDir: File, extension: String): List<File> {
    val directory = File.createTempFile("catalog-", "", filesDir)
    if (!directory.delete() || !directory.mkdir()) {
        throw IOException("Cannot create catalog extraction directory")
    }
    try {
        zipFile.unzip(directory.path)
        // Catalog packages contain top-level JSON files. Never import leftovers from filesDir.
        val files = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(extension) }
        if (files.isEmpty() || files.any { it.length() == 0L }) {
            throw IOException("Archive contains no valid catalog files")
        }
        return files
    } catch (e: Exception) {
        directory.deleteRecursively()
        throw e
    }
}

@Throws(IOException::class)
fun File.unzip(destDirectory: String) {
    extractZip(File(destDirectory))
}

/** Check declared sizes before writing, and enforce the same limit on actual streamed bytes. */
internal fun File.extractZip(
    destination: File,
    maxEntries: Int = 10_000,
    maxBytes: Long = 512L * 1024 * 1024,
) {
    val root = destination.canonicalFile
    if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create extraction directory")
    val prefix = root.path + File.separator
    ZipFile(this).use { zip ->
        val entries = zip.entries().asSequence().take(maxEntries + 1).toList()
        if (entries.size > maxEntries) throw IOException("Too many ZIP entries")
        val paths = mutableSetOf<String>()
        var declaredBytes = 0L
        val targets = entries.map { entry ->
            val name = entry.name.replace('\\', '/')
            val target = File(root, name).canonicalFile
            if (name.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(name) ||
                !target.path.startsWith(prefix) || !paths.add(target.path)
            ) {
                throw IOException("Unsafe or duplicate ZIP entry: ${entry.name}")
            }
            if (entry.size > maxBytes - declaredBytes) throw IOException("ZIP size limit exceeded")
            if (entry.size > 0) declaredBytes += entry.size
            entry to target
        }
        var extractedBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        for ((entry, target) in targets) {
            if (entry.isDirectory) {
                if (!target.isDirectory && !target.mkdirs()) throw IOException("Cannot create ZIP directory")
                continue
            }
            if (!target.parentFile.isDirectory && !target.parentFile.mkdirs()) {
                throw IOException("Cannot create ZIP parent directory")
            }
            zip.getInputStream(entry).use { input ->
                FileOutputStream(target).use { output ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (count.toLong() > maxBytes - extractedBytes) throw IOException("ZIP size limit exceeded")
                        output.write(buffer, 0, count)
                        extractedBytes += count
                    }
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