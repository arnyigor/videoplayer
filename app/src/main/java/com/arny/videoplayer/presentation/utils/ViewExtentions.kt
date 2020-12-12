package com.arny.videoplayer.presentation.utils

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView

@SuppressLint("ClickableViewAccessibility")
fun EditText.setDrawableRightListener(onClick: () -> Unit) {
    this.setOnTouchListener { v, event ->
        if (v is TextView) {
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (v.right - v.compoundDrawables[2].bounds.width())) {
                    onClick.invoke()
                    return@setOnTouchListener true
                }
            }
        }
        return@setOnTouchListener false
    }
}

fun EditText.setEnterPressListener(onEnterPressed: () -> Unit) {
    this.setOnKeyListener { _, keyCode, event ->
        if ((event.action == KeyEvent.ACTION_DOWN) &&
            (keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            onEnterPressed()
            return@setOnKeyListener true
        }
        return@setOnKeyListener false
    }
}