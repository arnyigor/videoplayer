package com.arny.mobilecinema.presentation.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.addTextChangedListener
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

inline fun Spinner.updateSpinnerItems(
    listener: AdapterView.OnItemSelectedListener?,
    onUpdate: () -> Unit? = {}
) {
    this.onItemSelectedListener = null
    onUpdate.invoke()
    this.onItemSelectedListener = listener
}

fun EditText.getQueryTextChangeStateFlow(): StateFlow<String> {
    val query = MutableStateFlow("")
    addTextChangedListener {
        if (this.isFocused) {
            query.value = it.toString()
        }
    }
    return query
}

fun View.showSnackBar(message: String, duration: Int = BaseTransientBottomBar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.showSnackBar(
    message: String,
    actionText: String,
    duration: Int? = null,
    @ColorInt actionColor: Int? = null,
    action: () -> Unit
) {
    val snackBar = Snackbar.make(this, message, duration ?: Snackbar.LENGTH_INDEFINITE)
    snackBar.setAction(actionText) { action.invoke() }
    if (actionColor != null) {
        snackBar.setActionTextColor(actionColor)
    }
    snackBar.show()
}