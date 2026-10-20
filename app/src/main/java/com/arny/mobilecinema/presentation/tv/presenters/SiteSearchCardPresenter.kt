package com.arny.mobilecinema.presentation.tv.presenters

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.bumptech.glide.Glide
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Карточка результата поиска по сайту. В БД карточки ещё нет — только предпросмотр.
 */
class SiteSearchCardPresenter : Presenter(), KoinComponent {

    private val prefs: Prefs by inject()

    companion object {
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        TvCardFocusHelper.setup(cardView)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val siteItem = item as? SiteSearchItem ?: return
        val cardView = viewHolder.view as ImageCardView
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()

        val contentText = buildList {
            if (siteItem.year > 0) add(siteItem.year.toString())
            add(
                when (siteItem.type) {
                    MovieType.SERIAL -> cardView.context.getString(R.string.serial)
                    MovieType.CINEMA -> cardView.context.getString(R.string.cinema)
                    else -> ""
                }
            )
            if (siteItem.rating > 0f) add(String.format("%.1f", siteItem.rating))
        }.filter { it.isNotBlank() }.joinToString(" • ")

        cardView.apply {
            titleText = siteItem.title
            this.contentText = contentText
            contentDescription = listOf(titleText?.toString(), contentText)
                .filter { !it.isNullOrBlank() }
                .joinToString(", ")

            TvCardFocusHelper.applyFocusState(this, hasFocus())

            if (siteItem.img.isNotBlank()) {
                Glide.with(context)
                    .load(siteItem.img.getWithDomain(baseUrl))
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_movie)
                    .error(R.drawable.placeholder_movie)
                    .into(mainImageView!!)
            } else {
                mainImageView?.setImageResource(R.drawable.placeholder_movie)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        TvCardFocusHelper.reset(cardView)
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
