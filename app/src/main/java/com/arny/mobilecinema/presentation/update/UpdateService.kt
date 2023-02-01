package com.arny.mobilecinema.presentation.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.data.utils.unzip
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.models.MoviesData
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.google.gson.Gson
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class UpdateService : LifecycleService(), CoroutineScope {
    private companion object {
        const val NOTICE_ID = 1001
    }

    @Inject
    lateinit var repository: UpdateRepository

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        startForeground(
            NOTICE_ID,
            getNotice("channelId", "channelName", "Обновление")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleScope.launch(coroutineContext) {
            try {
                update(intent)
            } catch (e: Exception) {
                // TODO: Отобразить ошибку и записать
                e.printStackTrace()
                stop()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun update(intent: Intent?) {
        withContext(Dispatchers.IO) {
            val filePath = intent?.getStringExtra("file")
            if (filePath != null) {
                val file = File(filePath)
                val dataFile = unzipData(file, context = applicationContext)
                if (dataFile != null) {
                    val anwapMovies = readData(dataFile)
                    if (anwapMovies.isNotEmpty()) {
                        file.delete()
                        dataFile.delete()
                        repository.updateMovies(anwapMovies)
                        repository.setLastUpdate()
                    }
                    stop()
                }
            }
        }
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            stopSelf()
        }
    }

    private fun unzipData(zipFile: File, context: Context): File? {
        val path = context.filesDir.path
        zipFile.unzip(path)
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
        return dataFile
    }

    private fun readData(file: File): List<AnwapMovie> =
        Gson().fromJson(
            FileReader(file),
            MoviesData::class.java
        ).movies

    private fun Context.getNotice(
        channelId: String,
        channelName: String,
        title: String
    ): Notification {
        return getNotificationBuilder(channelId, channelName)
            .apply {
                setContentText("Обновление")
                setContentTitle(title)
                setAutoCancel(false)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }.build()
    }

    private fun Context.getNotificationBuilder(
        channelId: String,
        channelName: String
    ): NotificationCompat.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(
                /* context = */ this,
                /* channelId = */ createNotificationChannel(
                    context = this,
                    channelId = channelId,
                    channelName = channelName
                )
            )
        } else {
            NotificationCompat.Builder(this)
        }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String
    ): String {
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        service?.createNotificationChannel(chan)
        return channelId
    }
}