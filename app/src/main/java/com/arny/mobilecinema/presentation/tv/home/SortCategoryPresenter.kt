package com.arny.mobilecinema.presentation.tv.home

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R

class SortCategoryPresenter : Presenter() {

    private var selectedPosition = 0

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
        val category = item as? MovieSortCategory ?: return
        val textView = viewHolder.view as TextView

        textView.text = textView.context.getString(category.labelResId)

        val isSelected = category.ordinal == selectedPosition
        textView.setTextColor(
            ContextCompat.getColor(
                textView.context,
                if (isSelected) R.color.white else R.color.sort_category_text
            )
        )
        textView.isSelected = isSelected
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
    }
}