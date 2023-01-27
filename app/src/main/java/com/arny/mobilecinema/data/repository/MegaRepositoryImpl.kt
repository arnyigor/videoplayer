package com.arny.mobilecinema.data.repository

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.utils.MegaHandler
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.unzipFile
import com.arny.mobilecinema.domain.repository.MegaRepository
import java.io.File
import javax.inject.Inject

class MegaRepositoryImpl @Inject constructor(
    private val megaHandler: MegaHandler,
    private val context: Context
) : MegaRepository {
    override fun downloadDB(): Boolean {
        val zipFile = File(context.filesDir, "tmp.zip")
        val regex = ".*/file/(.+?)#(.+?)$".toRegex()
        val groups = regex.matchEntire(BuildConfig.dblink)?.groups
        val id = groups?.get(1)?.value.orEmpty()
        val part2 = groups?.get(2)?.value.orEmpty()
        megaHandler.download(zipFile.absolutePath, id, part2)
        return true
    }

    override fun unzipFile(): File {
        println("unzipFile started")
        val zipFile = File(context.filesDir, "tmp.zip")
        val path = context.filesDir.path
        unzipFile(zipFile.path, path)
        var dataFile: File? = null
        File(path).listFiles()?.forEach { file ->
            println("file:${file.name}, lenth:${formatFileSize(file.length())}")
            if (file.name == "data.json") {
                dataFile = file
            }
        }
        println("unzipFile finish")
        return dataFile?: error("")
    }
}