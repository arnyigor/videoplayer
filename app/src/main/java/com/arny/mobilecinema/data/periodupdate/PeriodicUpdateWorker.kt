package com.arny.mobilecinema.data.periodupdate

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.utils.sendServiceMessage

class PeriodicUpdateWorker(
    val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            context.sendServiceMessage(
                Intent(context.applicationContext, UpdateService::class.java),
                AppConstants.ACTION_UPDATE_ALL
            )
        } catch (ex: Exception) {
            return Result.failure()
        }
        return Result.success()
    }
}