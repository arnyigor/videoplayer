package com.arny.mobilecinema.domain.interactors.feedback

import android.content.Context
import android.os.Build
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.utils.getAvailableMemory
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class FeedbackInteractorImpl @Inject constructor(
    private val context: Context
) : FeedbackInteractor {

    private var lastPlayerError: String = ""

    override fun sendMessage(
        content: String,
        movie: Movie?,
        seasonPosition: Int,
        episodePosition: Int
    ): Flow<DataResult<Boolean>> = doAsync {
        TODO("Поменять на телеграм фидбек,можно подключить новое приложение")
    }

    override fun setLastError(error: String) {
        lastPlayerError = error
    }

    private fun getSystemDetail(): String {
        return "Brand: ${Build.BRAND}; " +
                "Model: ${Build.MODEL}; " +
                "DEVICE: ${Build.DEVICE}; " +
                "SDK: ${Build.VERSION.SDK_INT}; " +
                "Version Code: ${Build.VERSION.RELEASE}; " +
                "AvailMemory: ${formatFileSize(context.getAvailableMemory().availMem)}; "
    }

    private fun getAppDetail(): String {
        return "APP VERSION_CODE: ${BuildConfig.VERSION_CODE}; " +
                "APP VERSION_NAME: ${BuildConfig.VERSION_NAME}; "
    }
}