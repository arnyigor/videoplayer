package com.arny.mobilecinema.presentation.tv.details

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.bumptech.glide.Glide
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TvEpisodeCardPresenter(
    private val cardWidth: Int = 313,
    private val cardHeight: Int = 176
) : Presenter(), KoinComponent {

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
        val episodeItem = item as? EpisodeItem ?: return
        val episode = episodeItem.episode
        val cardView = viewHolder.view as ImageCardView

        // Улучшенное отображение названия
        cardView.titleText = when {
            episode.title.isNotBlank() -> episode.title
            else -> "Серия ${episode.episode}"
        }

        // Дополнительная информация
        cardView.contentText = buildString {
            append("Серия ${episode.episode}")
            if (episode.title.isNotBlank() && episode.episode.isNotBlank()) {
                // Если есть и номер и название, показываем номер в subtitle
            }
        }

        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        val fullUrl = episode.poster.getWithDomain(baseUrl)

        if (fullUrl.isNotBlank()) {
            Glide.with(cardView.context)
                .load(fullUrl)
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