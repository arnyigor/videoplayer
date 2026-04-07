package com.arny.mobilecinema.presentation.tv.details

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.bumptech.glide.Glide
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TvEpisodeCardPresenter(
    private val cardWidth: Int = 240,
    private val cardHeight: Int = 135
) : Presenter(), KoinComponent { // 1. Добавили KoinComponent

    // 2. Инжектим Prefs
    private val prefs: Prefs by inject()

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
        val episode = when (item) {
            is SerialEpisode -> item
            is EpisodeItem -> item.episode
            else -> return
        }

        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = episode.title.ifBlank { "Серия ${episode.episode}" }
        cardView.contentText = episode.episode

        // 3. Получаем baseUrl и склеиваем ссылку
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        val fullUrl = episode.poster.getWithDomain(baseUrl)

        if (fullUrl.isNotBlank()) {
            Glide.with(cardView.context)
                .load(fullUrl) // 4. Загружаем склеенный URL
                .centerCrop()
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .into(cardView.mainImageView!!)
        } else {
            cardView.mainImageView?.setImageResource(R.drawable.placeholder_movie)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}