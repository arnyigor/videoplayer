package com.arny.mobilecinema.presentation.tv.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ActionCardBinding

class TvActionCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val binding = ActionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val cardView = binding.root
        cardView.isFocusable = true
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val binding = ActionCardBinding.bind(viewHolder.view)
        val action = item as? Action ?: return
        binding.titleView.text = action.label1

        applyState(binding, viewHolder.view.hasFocus())

        viewHolder.view.setOnFocusChangeListener { _, hasFocus ->
            applyState(binding, hasFocus)
        }
    }

    private fun applyState(binding: ActionCardBinding, hasFocus: Boolean) {
        val context = binding.root.context
        val card = binding.root

        val bgColor = if (hasFocus) {
            ContextCompat.getColor(context, R.color.colorAccent)
        } else {
            ContextCompat.getColor(context, R.color.card_dark_bg)
        }

        val titleColor = if (hasFocus) {
            ContextCompat.getColor(context, R.color.colorOnAccent)
        } else {
            ContextCompat.getColor(context, R.color.textColorPrimary)
        }

        card.setCardBackgroundColor(bgColor)
        binding.titleView.setTextColor(titleColor)

        binding.root.animate().cancel()
        val scale = if (hasFocus) 1.04f else 1f
        binding.root.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(120L)
            .start()
        card.cardElevation = if (hasFocus) 12f else 0f
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.onFocusChangeListener = null
    }
}
