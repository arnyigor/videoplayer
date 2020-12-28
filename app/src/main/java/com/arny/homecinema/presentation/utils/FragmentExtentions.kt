package com.arny.homecinema.presentation.utils

import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun AppCompatActivity.hideSystemBar() {
    val window = this.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    }
}

fun AppCompatActivity.showSystemBar() {
    val window = this.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(true)
    } else {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
