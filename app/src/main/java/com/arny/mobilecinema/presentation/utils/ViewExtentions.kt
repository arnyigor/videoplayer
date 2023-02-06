package com.arny.mobilecinema.presentation.utils

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.TextView.BufferType
import androidx.annotation.ColorInt
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import com.arny.mobilecinema.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
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

fun EditText.textChanges(): Flow<CharSequence?> =
    callbackFlow {
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

fun TextView.makeTextViewResizable(
    maxLine: Int = 3,
    endText: String = context.getString(R.string.more)
) {
    val tv = this
    if (tv.tag == null) {
        tv.tag = tv.text
    }
    tv.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
        @Suppress("deprecation")
        override fun onGlobalLayout() {
            val obs = tv.viewTreeObserver
            obs.removeGlobalOnLayoutListener(this)
            when {
                maxLine == 0 -> {
                    val lineEndIndex = tv.layout.getLineEnd(0)
                    val text = tv.text.subSequence(0, lineEndIndex - endText.length + 1)
                        .toString() + " " + endText
                    tv.text = text
                    tv.movementMethod = LinkMovementMethod.getInstance()
                    tv.addClickablePartTextViewResizable(maxLine)
                }

                maxLine > 0 && tv.lineCount >= maxLine -> {
                    val lineEndIndex = tv.layout.getLineEnd(maxLine - 1)
                    val text = tv.text.subSequence(0, lineEndIndex - endText.length + 1)
                        .toString() + " " + endText
                    tv.text = text
                    tv.movementMethod = LinkMovementMethod.getInstance()
                    tv.addClickablePartTextViewResizable(maxLine)
                }

                else -> {
                    val lineEndIndex = tv.layout.getLineEnd(tv.layout.lineCount - 1)
                    val text = tv.text.subSequence(0, lineEndIndex)
                        .toString() + " " + endText
                    tv.text = text
                    tv.movementMethod = LinkMovementMethod.getInstance()
                    tv.addClickablePartTextViewResizable(maxLine)
                }
            }
        }
    })
}

private fun TextView.addClickablePartTextViewResizable(maxLine: Int = 3) {
    val tv = this
    val context = tv.context
    val expandText: String = context.getString(R.string.more)
    val collapseText: String = context.getString(R.string.less)
    val strSpanned = tv.text.toString()
    val ssb = SpannableStringBuilder(strSpanned)
    when {
        strSpanned.endsWith(expandText) -> {
            ssb.setSpan(
                /* what = */ object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        tv.layoutParams = tv.layoutParams
                        tv.setText(tv.tag.toString(), BufferType.SPANNABLE)
                        tv.invalidate()
                        tv.makeTextViewResizable(-maxLine, collapseText)
                        tv.setTextColor(context.getDrawableColor(R.color.textColorPrimary))
                    }
                },
                /* start = */ strSpanned.indexOf(expandText),
                /* end = */ strSpanned.indexOf(expandText) + expandText.length,
                /* flags = */ 0
            )
        }

        strSpanned.endsWith(collapseText) -> {
            ssb.setSpan(
                /* what = */ object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        tv.layoutParams = tv.layoutParams
                        tv.setText(tv.tag.toString(), BufferType.SPANNABLE)
                        tv.invalidate()
                        tv.makeTextViewResizable(-maxLine, expandText)
                        tv.setTextColor(context.getDrawableColor(R.color.textColorPrimary))
                    }
                },
                /* start = */ strSpanned.indexOf(collapseText),
                /* end = */ strSpanned.indexOf(collapseText) + collapseText.length,
                /* flags = */ 0
            )
        }
    }
    tv.setText(ssb, BufferType.SPANNABLE)
}