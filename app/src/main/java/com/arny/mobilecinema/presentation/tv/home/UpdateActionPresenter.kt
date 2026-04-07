package com.arny.mobilecinema.presentation.tv.home

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R

class UpdateActionPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(48, 24, 48, 24)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.tv_button_background)
            setTextColor(context.getColor(R.color.textColorPrimary))
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? UpdateAction ?: return
        val textView = viewHolder.view as TextView

        textView.text = when (action) {
            UpdateAction.CHECK_UPDATE -> textView.context.getString(R.string.check_update)
            UpdateAction.CANCEL_UPDATE -> textView.context.getString(R.string.cancel_update)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Nothing to clean up
    }
}