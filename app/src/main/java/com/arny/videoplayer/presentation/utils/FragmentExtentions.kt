package com.arny.videoplayer.presentation.utils

import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

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

/**
 * Runs a FragmentTransaction, then calls commit().
 */
inline fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply {
        action()
    }.commitAllowingStateLoss()
}