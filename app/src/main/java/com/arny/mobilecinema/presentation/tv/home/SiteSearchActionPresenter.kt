package com.arny.mobilecinema.presentation.tv.home

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R

/** Единственное действие этого ряда: открыть поиск по каталогу сайта. */
enum class SiteSearchAction {
    SITE_SEARCH
}

class SiteSearchActionPresenter : Presenter() {

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
            setTextColor(ContextCompat.getColorStateList(context, R.color.tv_button_text_selector))
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? SiteSearchAction ?: return
        val textView = viewHolder.view as TextView
        textView.text = when (action) {
            SiteSearchAction.SITE_SEARCH -> textView.context.getString(R.string.site_search_title)
        }
        textView.contentDescription = textView.text
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
