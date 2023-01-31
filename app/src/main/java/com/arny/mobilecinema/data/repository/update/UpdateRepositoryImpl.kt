package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.domain.repository.UpdateRepository
import java.io.File
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val context: Context
) : UpdateRepository {
    override var updateDownloadId: Long = -1L

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
}