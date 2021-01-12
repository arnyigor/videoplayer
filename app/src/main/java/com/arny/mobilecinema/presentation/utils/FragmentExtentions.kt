package com.arny.mobilecinema.presentation.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.arny.mobilecinema.data.models.DataThrowable

fun AppCompatActivity.hideSystemBar() {
    val window = this.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    }
}

fun Activity.lockOrientation() {
    val rotation = this.display?.rotation
    val orientLand = rotation == ActivityInfo.SCREEN_ORIENTATION_BEHIND
            || rotation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    requestedOrientation = if (orientLand && land) {
        val sensorLand = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (rotation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) sensorLand else rotation ?: sensorLand
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

fun Activity.unlockOrientation() {
    this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

fun AppCompatActivity.showSystemBar() {
    val window = this.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(true)
    } else {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

inline fun <reified T : Any> newIntent(context: Context): Intent = Intent(context, T::class.java)
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
