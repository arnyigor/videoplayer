package com.arny.homecinema.presentation.utils

import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

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

fun AppCompatActivity.replaceFragment(
    fragment: Fragment,
    @IdRes frameId: Int,
    addToback: Boolean = false,
    tag: String? = null,
    targetFragment: Fragment? = null,
    requestCode: Int? = null,
    onLoadFunc: () -> Unit? = {}
) {
    val tg = tag ?: fragment.javaClass.canonicalName
    val curFragment = getFragmentByTag(tg) ?: fragment
    if (targetFragment != null) {
        curFragment.setTargetFragment(targetFragment, requestCode ?: 0)
    }
    supportFragmentManager.transact {
        replace(frameId, curFragment, tg)
        if (addToback) {
            addToBackStack(tag)
        }
    }
    onLoadFunc()
}

fun AppCompatActivity.getFragmentByTag(tag: String?): Fragment? {
    return supportFragmentManager.findFragmentByTag(tag)
}

fun Fragment.toast(@StringRes res: Int) {
    Toast.makeText(
        requireContext(),
        getString(res),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * Runs a FragmentTransaction, then calls commit().
 */
inline fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply {
        action()
    }.commitAllowingStateLoss()
}