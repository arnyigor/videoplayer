package com.arny.homecinema.presentation.utils

import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun AppCompatActivity.setSystemBarVisible(visible: Boolean) {
    val window = this.window
    if (visible) {
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}

fun AppCompatActivity.setActionBarVisible(visible: Boolean) {
    if (visible) {
        supportActionBar?.show()
    } else {
        supportActionBar?.hide()
    }
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
