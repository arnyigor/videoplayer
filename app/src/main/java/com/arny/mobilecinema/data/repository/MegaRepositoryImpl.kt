package com.arny.mobilecinema.data.repository

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.unzipFile
import com.arny.mobilecinema.domain.repository.MegaRepository
import java.io.File
import javax.inject.Inject

class MegaRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val context: Context
) : MegaRepository {
    override suspend fun downloadDataFile(): Boolean {
        val zipFile = File(context.filesDir, "tmp.zip")
        val downloadUrl: String = BuildConfig.data_link
        apiService.downloadFile(zipFile.path, downloadUrl)
        return true
    }

    override fun unzipFile(): File {
        println("unzipFile started")
        val zipFile = File(context.filesDir, "tmp.zip")
        val path = context.filesDir.path
        unzipFile(zipFile.path, path)
        var dataFile: File? = null
        File(path).listFiles()?.forEach { file ->
            println("file:${file.name}, length:${formatFileSize(file.length())}")
            if (file.name == "data.json") {
                dataFile = file
            }
        }
        println("unzipFile finish")
        return dataFile?: error("")
    }
}