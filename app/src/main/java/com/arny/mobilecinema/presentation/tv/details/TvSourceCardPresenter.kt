package com.arny.mobilecinema.presentation.tv.details

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
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
        val binding = ItemTvSourceCardBinding.bind(viewHolder.view)

        when (item) {
            is SourceItem -> bindSource(binding, item)
            is SeasonItem -> bindSeason(binding, item)
            is TagItem -> bindTag(binding, item)
            else -> {
                binding.tvSourceLabel.text = ""
                binding.tvQualityBadge.visibility = View.GONE
                binding.tvSourceHint.visibility = View.GONE
            }
        }

        val selected = false
        applyState(binding, viewHolder.view.hasFocus(), selected)

        viewHolder.view.setOnFocusChangeListener { _, hasFocus ->
            applyState(binding, hasFocus, selected)
        }
    }

    private fun bindSource(binding: ItemTvSourceCardBinding, source: SourceItem) {
        binding.tvSourceLabel.text = source.label
        binding.tvSourceHint.visibility = View.VISIBLE
        binding.tvSourceHint.text = binding.root.context.getString(R.string.click_to_play)

        binding.tvQualityBadge.visibility = if (source.quality.isNotBlank()) {
            binding.tvQualityBadge.text = source.quality
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun bindSeason(binding: ItemTvSourceCardBinding, season: SeasonItem) {
        val context = binding.root.context
        val seasonNumber = season.season.id ?: (season.seasonIndex + 1)
        val episodesCount = season.season.episodes.size

        binding.tvSourceLabel.text = context.getString(R.string.spinner_season) + " " + seasonNumber
        binding.tvQualityBadge.visibility = View.GONE
        binding.tvSourceHint.visibility = View.VISIBLE
        binding.tvSourceHint.text = context.resources.getQuantityString(
            R.plurals.episods,
            episodesCount,
            episodesCount
        )
    }

    private fun bindTag(binding: ItemTvSourceCardBinding, tag: TagItem) {
        binding.tvSourceLabel.text = tag.label
        binding.tvQualityBadge.visibility = View.GONE
        binding.tvSourceHint.visibility = View.VISIBLE
        binding.tvSourceHint.text = binding.root.context.getString(R.string.click_to_search)
    }

    private fun applyState(binding: ItemTvSourceCardBinding, hasFocus: Boolean, selected: Boolean) {
        val context = binding.root.context

        val bgColor = when {
            hasFocus -> ContextCompat.getColor(context, R.color.colorAccent)
            selected -> ContextCompat.getColor(context, R.color.card_light_bg)
            else -> ContextCompat.getColor(context, R.color.card_dark_bg)
        }

        val titleColor = if (hasFocus) {
            ContextCompat.getColor(context, R.color.colorOnAccent)
        } else {
            ContextCompat.getColor(context, R.color.textColorPrimary)
        }

        val hintColor = if (hasFocus) {
            ContextCompat.getColor(context, R.color.colorOnAccent)
        } else {
            ContextCompat.getColor(context, R.color.textColorSecondary)
        }

        binding.root.setCardBackgroundColor(bgColor)
        binding.tvSourceLabel.setTextColor(titleColor)
        binding.tvSourceHint.setTextColor(hintColor)

        if (hasFocus) {
            binding.tvQualityBadge.setTextColor(ContextCompat.getColor(context, R.color.colorOnAccent))
            binding.tvQualityBadge.setBackgroundColor(Color.TRANSPARENT)
        } else {
            binding.tvQualityBadge.setTextColor(Color.parseColor("#FFD700"))
            binding.tvQualityBadge.setBackgroundResource(R.drawable.bg_badge_imdb)
        }

        binding.root.scaleX = if (hasFocus) 1.03f else 1f
        binding.root.scaleY = if (hasFocus) 1.03f else 1f
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.setOnFocusChangeListener(null)
    }
}
