package com.arny.mobilecinema.presentation.utils

import android.content.Context
import android.content.pm.PackageManager

enum class DeviceType {
    PHONE,
    TABLET,
    TV
}

object DeviceUtils {

    fun getDeviceType(context: Context): DeviceType {
        val pm = context.packageManager
        return when {
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> DeviceType.TV
            isTablet(context) -> DeviceType.TABLET
            else -> DeviceType.PHONE
        }
    }

    fun isTV(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun isTablet(context: Context): Boolean {
        val config = context.resources.configuration
        val screenLayout = config.screenLayout and
            android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        return screenLayout >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }
}
