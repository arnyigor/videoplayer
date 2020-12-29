package com.arny.homecinema.presentation.utils

import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.arny.homecinema.data.models.DataThrowable

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
