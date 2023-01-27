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
        println("downloadDB started")
        val zipFile = File(context.filesDir, "tmp.zip")
        val regex = ".*/file/(.+?)#(.+?)$".toRegex()
        val groups = regex.matchEntire(BuildConfig.dblink)?.groups
        val id = groups?.get(1)?.value.orEmpty()
        val part2 = groups?.get(2)?.value.orEmpty()
        megaHandler.download(zipFile.absolutePath, id, part2)
        println("downloadDB finish")
        return true
    }

    override fun unzipFile(): Boolean {
        println("unzipFile started")
        val zipFile = File(context.filesDir, "tmp.zip")
        val path = context.filesDir.path
        unzipFile(zipFile.path, path)
        File(path).listFiles()?.forEach { file ->
            println("file:$file, lenth:${formatFileSize(file.length())}")
        }
        println("unzipFile finish")
        return true
    }
}