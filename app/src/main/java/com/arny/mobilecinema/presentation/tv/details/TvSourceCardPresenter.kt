package com.arny.mobilecinema.presentation.tv.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.databinding.ItemTvSourceCardBinding

class TvSourceCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val binding = ItemTvSourceCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val cardView = binding.root
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val source = item as? SourceItem ?: return
        val binding = ItemTvSourceCardBinding.bind(viewHolder.view)

        binding.tvSourceLabel.text = source.label

        // Показываем бейдж качества если есть
        binding.tvQualityBadge.visibility = if (source.quality.isNotBlank()) {
            binding.tvQualityBadge.text = source.quality
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}