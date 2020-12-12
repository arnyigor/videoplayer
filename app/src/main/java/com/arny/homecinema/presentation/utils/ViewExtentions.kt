package com.arny.homecinema.presentation.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
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
    this.setOnEditorActionListener { _, actionId, _ ->
        return@setOnEditorActionListener when (actionId) {
            EditorInfo.IME_ACTION_DONE -> {
                onEnterPressed()
                true
            }
            else -> false
        }
    }
}