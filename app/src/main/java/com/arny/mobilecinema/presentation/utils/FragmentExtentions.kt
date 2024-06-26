package com.arny.mobilecinema.presentation.utils

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString

fun NavController.navigateSafely(directions: NavDirections) {
    currentDestination?.getAction(directions.actionId)?.destinationId ?: return
    navigate(directions.actionId, directions.arguments, null)
}

fun Fragment.isNotificationsFullyEnabled(): Boolean {
    val notificationManagerCompat = NotificationManagerCompat.from(requireContext())
    if (!notificationManagerCompat.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        for (notificationChannel in notificationManagerCompat.notificationChannels) {
            if (!notificationChannel.isFullyEnabled(notificationManagerCompat)) return false
        }
    }
    return true
}

@RequiresApi(Build.VERSION_CODES.O)
fun NotificationChannel.isFullyEnabled(notificationManager: NotificationManagerCompat): Boolean {
    if (importance == NotificationManager.IMPORTANCE_NONE) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        if (notificationManager.getNotificationChannelGroup(group)?.isBlocked == true) return false
    }
    return true
}

fun Fragment.registerContentResolver(observer: ContentObserver) {
    requireContext().contentResolver.registerContentObserver(
        /* uri = */ android.provider.Settings.System.CONTENT_URI,
        /* notifyForDescendants = */ true,
        /* observer = */ observer
    )
}

fun Fragment.unregisterContentResolver(observer: ContentObserver) {
    requireContext().contentResolver.unregisterContentObserver(observer)
}

fun Fragment.initAudioManager(
    manager: AudioManager?,
    focusChangeListener: AudioManager.OnAudioFocusChangeListener
): AudioManager {
    val am = manager ?: requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.requestAudioFocus(
        focusChangeListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN
    )
    return am
}

fun Fragment.setScreenBrightness(value: Int) {
    val window = requireActivity().window
    val lp = window.attributes
    lp.screenBrightness = (1.0f / 30) * value
    window.attributes = lp
}

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
            || rotation == ActivityInfo.SCREEN_ORIENTATION_BEHIND
        ) sensorLand
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

fun Fragment.getString(string: IWrappedString?): String =
    string?.toString(requireContext()).orEmpty()

fun Fragment.toastError(throwable: Throwable?) {
    throwable?.printStackTrace()
    toast(
        when (throwable) {
            is DataThrowable -> getString(throwable.errorRes)
            else -> throwable?.message
        }
    )
}

fun Fragment.toast(text: String?, duration: Int = Toast.LENGTH_SHORT) {
    text?.let {
        Toast.makeText(
            requireContext(),
            it,
            duration
        ).show()
    }
}
