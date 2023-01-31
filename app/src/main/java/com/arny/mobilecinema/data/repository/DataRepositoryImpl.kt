package com.arny.mobilecinema.data.repository

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.data.utils.unzip
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.models.Data
import com.arny.mobilecinema.domain.repository.DataRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import javax.inject.Inject

class DataRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val context: Context
) : DataRepository {
    override suspend fun downloadDataFile(): File {
        val zipFile = File(context.filesDir, "tmp_${System.currentTimeMillis()}.zip")
        val downloadUrl: String = BuildConfig.data_link
        zipFile.create()
        apiService.downloadFile(zipFile, downloadUrl)
        return zipFile
    }

    override suspend fun downloadUpdate(): File {
        val downloadUrl: String = BuildConfig.update_link
        val file = File(context.filesDir, "update.txt")
        file.delete()
        file.create()
        apiService.downloadFile(file, downloadUrl)
        return file
    }

    override fun getLastUpdate(): String {
        return Prefs.getInstance(context).get<String>(PrefsConstants.LAST_DATA_UPDATE).orEmpty()
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

    override fun readFile(file: File): List<AnwapMovie> {
        return Gson().fromJson(FileReader(file), Data::class.java).movies
    }
}