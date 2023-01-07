package com.arny.mobilecinema.presentation.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.arny.mobilecinema.data.models.DataThrowable

fun Window.hideSystemBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setDecorFitsSystemWindows(false)
    } else {
        decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
    }
}

fun Window.hideSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
         insetsController?.hide(WindowInsets.Type.systemBars())
    } else {
        val decorView = decorView
        var uiVisibility = decorView.systemUiVisibility
        uiVisibility = uiVisibility or View.SYSTEM_UI_FLAG_LOW_PROFILE
        uiVisibility = uiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        uiVisibility = uiVisibility or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        uiVisibility = uiVisibility or View.SYSTEM_UI_FLAG_IMMERSIVE
        uiVisibility = uiVisibility or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility = uiVisibility
    }
}

fun Window.showSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
         insetsController?.show(WindowInsets.Type.systemBars())
    } else {
        val decorView = decorView
        var uiVisibility = decorView.systemUiVisibility
        uiVisibility = uiVisibility and View.SYSTEM_UI_FLAG_LOW_PROFILE.inv()
        uiVisibility = uiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
        uiVisibility = uiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
        uiVisibility = uiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE.inv()
        uiVisibility = uiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        decorView.systemUiVisibility = uiVisibility
    }
}

fun Window.setFullscreen(on: Boolean) {
    val winParams: WindowManager.LayoutParams = attributes
    val bits = WindowManager.LayoutParams.FLAG_FULLSCREEN
    if (on) {
        winParams.flags = winParams.flags or bits
    } else {
        winParams.flags = winParams.flags and bits.inv()
    }
    attributes = winParams
}

fun Window.setTranslucentNavigation(on: Boolean) {
    val winParams = attributes
    val bits = WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
    if (on) {
        winParams.flags = winParams.flags or bits
    } else {
        winParams.flags = winParams.flags and bits.inv()
    }
    attributes = winParams
}

@RequiresApi(Build.VERSION_CODES.R)
fun Activity.lockOrientation() {
    val rotation = this.display?.rotation
    val orientLand = rotation == ActivityInfo.SCREEN_ORIENTATION_BEHIND
            || rotation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    requestedOrientation = if (orientLand && land) {
        val sensorLand = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (rotation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            || rotation == ActivityInfo.SCREEN_ORIENTATION_BEHIND) sensorLand
        else rotation ?: sensorLand
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

fun Activity.unlockOrientation() {
    this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

fun Window.showSystemBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setDecorFitsSystemWindows(true)
    } else {
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
fun newIntent(): Intent = Intent()

fun Fragment.launchIntent(
    requestCode: Int = -1,
    options: Bundle? = null,
    init: Intent.() -> Unit = {}
) {
    val context = this.context
    if (context != null) {
        val intent = newIntent()
        intent.init()
        startActivityForResult(intent, requestCode, options)
    }
}

fun Fragment.toastError(throwable: Throwable?) {
    throwable?.printStackTrace()
    toast(
        when (throwable) {
            is DataThrowable -> getString(throwable.errorRes)
            else -> throwable?.message
        }
    )
}

fun Fragment.toast(text: String?) {
    text?.let {
        Toast.makeText(
            requireContext(),
            it,
            Toast.LENGTH_SHORT
        ).show()
    }
}
