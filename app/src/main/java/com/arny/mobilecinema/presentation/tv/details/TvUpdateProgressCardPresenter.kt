package com.arny.mobilecinema.presentation.tv.details

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ItemTvUpdateProgressBinding

/**
 * Item для строки-индикатора обновления данных на экране деталей (TV).
 * Клик по карточке отменяет обновление.
 */
data class UpdateProgressItem(
    val percent: Int = -1,
    val stage: String? = null,
)

class TvUpdateProgressCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val binding = ItemTvUpdateProgressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val cardView = binding.root
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.foreground = ContextCompat.getDrawable(
            parent.context,
            R.drawable.tv_card_focus_foreground
        )

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val binding = ItemTvUpdateProgressBinding.bind(viewHolder.view)

        val progressItem = item as? UpdateProgressItem
        val context = binding.root.context

        if (progressItem != null) {
            val percent = progressItem.percent
            binding.progressBar.isIndeterminate = percent !in 0..100
            if (percent in 0..100) {
                binding.progressBar.progress = percent
                binding.tvProgressPercent.text = "$percent%"
                binding.tvProgressPercent.isVisible = true
            } else {
                binding.tvProgressPercent.isVisible = false
            }
            binding.tvProgressTitle.text = progressItem.stage
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.updating_data)
        } else {
            binding.progressBar.isIndeterminate = true
            binding.tvProgressPercent.isVisible = false
            binding.tvProgressTitle.text = context.getString(R.string.updating_data)
        }

        applyState(binding, viewHolder.view.hasFocus())

        viewHolder.view.setOnFocusChangeListener { _, hasFocus ->
            applyState(binding, hasFocus)
        }
    }

    private fun applyState(binding: ItemTvUpdateProgressBinding, hasFocus: Boolean) {
        val context = binding.root.context

        binding.root.setCardBackgroundColor(
            if (hasFocus) ContextCompat.getColor(context, R.color.colorAccent)
            else ContextCompat.getColor(context, R.color.card_dark_bg)
        )
        binding.tvProgressTitle.setTextColor(
            if (hasFocus) ContextCompat.getColor(context, R.color.colorOnAccent)
            else ContextCompat.getColor(context, R.color.textColorPrimary)
        )
        binding.tvProgressPercent.setTextColor(
            if (hasFocus) ContextCompat.getColor(context, R.color.colorOnAccent)
            else ContextCompat.getColor(context, R.color.textColorSecondary)
        )
        binding.tvProgressHint.setTextColor(
            if (hasFocus) ContextCompat.getColor(context, R.color.colorOnAccent)
            else ContextCompat.getColor(context, R.color.textColorSecondary)
        )
        binding.tvCancelLabel.setTextColor(
            if (hasFocus) ContextCompat.getColor(context, R.color.colorOnAccent)
            else ContextCompat.getColor(context, R.color.colorAccent)
        )

        binding.root.animate().cancel()
        val scale = if (hasFocus) 1.04f else 1f
        binding.root.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(120L)
            .start()
        binding.root.cardElevation = if (hasFocus) 12f else 0f
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.setOnFocusChangeListener(null)
    }
}
