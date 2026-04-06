package com.arny.mobilecinema.presentation.tv.details

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.bumptech.glide.Glide

/**
 * Presenter для отображения карточки эпизода сериала на TV.
 *
 * Наследует [Presenter] и создаёт [ImageCardView] для каждого эпизода.
 * Используется в списке сезонов на экране деталей сериала.
 *
 * @property cardWidth ширина карточки в dp
 * @property cardHeight высота карточки в dp
 */
class TvEpisodeCardPresenter(
    private val cardWidth: Int = 240,
    private val cardHeight: Int = 135
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(cardWidth, cardHeight)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val episode = item as? SerialEpisode ?: return
        val cardView = viewHolder.view as ImageCardView

        // Название эпизода
        cardView.titleText = episode.title.ifBlank {
            "Серия ${episode.episode}"
        }

        // Описание
        cardView.contentText = episode.episode

        // Обложка эпизода (если есть)
        if (episode.poster.isNotBlank()) {
            com.bumptech.glide.Glide.with(cardView.context)
                .load(episode.poster)
                .centerCrop()
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .into(cardView.mainImageView!!)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
