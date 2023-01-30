package com.arny.mobilecinema.data.repository

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.data.utils.unzip
import com.arny.mobilecinema.domain.repository.DataRepository
import java.io.File
import javax.inject.Inject

class DataRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val context: Context
) : DataRepository {
    override suspend fun downloadDataFile(): File {
        val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
        val downloadUrl: String = BuildConfig.data_link
        apiService.downloadFile(zipFile, downloadUrl)
        return zipFile
    }

    override fun unzipFile(zipFile: File): File {
        val path = context.filesDir.path
        unzip(zipFile, path)
        var dataFile: File? = null
        val files = File(path).listFiles()
        files?.forEach { file ->
            if (file.name == "data.json") {
                dataFile = file
            }
        }
        if (dataFile != null && dataFile!!.isFileExists() && dataFile!!.length() > 0) {
            zipFile.delete()
        }
        return dataFile ?: error("Не найден файл")
    }
}