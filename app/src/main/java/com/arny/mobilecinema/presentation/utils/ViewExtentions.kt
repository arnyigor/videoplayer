package com.arny.mobilecinema.presentation.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import com.google.android.exoplayer2.util.Assertions.checkMainThread
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

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

@ExperimentalCoroutinesApi
@CheckResult
fun EditText.textChanges(): Flow<CharSequence?> =
    callbackFlow {
        checkMainThread()
        val listener = doOnTextChanged { text, _, _, _ -> trySend(text) }
        awaitClose { removeTextChangedListener(listener) }
    }.onStart { emit(text) }

fun EditText.getQueryTextChangeStateFlow(): StateFlow<String> {
    val query = MutableStateFlow("")
    addTextChangedListener {
        if (this.isFocused) {
            query.value = it.toString()
        }
    }
    return query
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