package com.arny.mobilecinema.presentation.tv.details

import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
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

        val seasonNumber = episodeItem.seasonIndex + 1
        val episodeLabel = episode.episode.ifBlank { (episodeItem.episodeIndex + 1).toString() }

        cardView.titleText = episode.title.ifBlank {
            "Серия $episodeLabel"
        }

        cardView.contentText = "Сезон $seasonNumber • Серия $episodeLabel"

        cardView.setInfoAreaBackgroundColor(
            ContextCompat.getColor(cardView.context, R.color.card_dark_bg)
        )

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
