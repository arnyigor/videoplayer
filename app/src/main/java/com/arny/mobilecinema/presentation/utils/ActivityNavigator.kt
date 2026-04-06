package com.arny.mobilecinema.presentation.utils

import android.content.Context
import android.content.Intent
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.tv.TvMainActivity

object ActivityNavigator {
    /**
     * Returns Intent to the correct main Activity
     * depending on platform (TV or Phone)
     */
    fun getMainActivityIntent(context: Context): Intent {
        val targetClass = if (DeviceUtils.isTV(context)) {
            TvMainActivity::class.java
        } else {
            MainActivity::class.java
        }
        return Intent(context, targetClass)
    }
}
