package com.arny.mobilecinema.presentation.tv.details

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R

class TvActionCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(32, 16, 32, 16)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_sort_category_selector)
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val textView = viewHolder.view as TextView
        val action = item as? Action ?: return
        textView.text = action.label1

        textView.setTextColor(
            ContextCompat.getColorStateList(textView.context, R.color.tv_chip_text_selector)
        )
        textView.isSelected = viewHolder.view.hasFocus()
        textView.contentDescription = textView.text
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.onFocusChangeListener = null
    }
}
