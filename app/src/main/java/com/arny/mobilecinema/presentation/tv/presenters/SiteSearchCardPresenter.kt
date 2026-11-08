package com.arny.mobilecinema.presentation.tv.presenters

import com.arny.mobilecinema.presentation.sitesearch.ratingLabel
import com.arny.mobilecinema.presentation.utils.loadPoster
import com.arny.mobilecinema.presentation.utils.clearPoster

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.presentation.utils.dpToPx
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.search.SearchHit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Карточка результата единого поиска. У локального тайтла есть `stableId`, у внешнего —
 * только `siteRef`: в БД он попадёт при открытии.
 */
class SiteSearchCardPresenter : Presenter(), KoinComponent {

    private val prefs: Prefs by inject()

    companion object {
        private const val CARD_WIDTH_DP = 156
        private const val CARD_HEIGHT_DP = 88
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(
                CARD_WIDTH_DP.dpToPx(context),
                CARD_HEIGHT_DP.dpToPx(context)
            )
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        TvCardFocusHelper.setup(cardView)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val siteItem = item as? SearchHit ?: return
        val cardView = viewHolder.view as ImageCardView
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()

        val contentText = buildList {
            if (siteItem.year > 0) add(siteItem.year.toString())
            add(
                when (siteItem.kind) {
                    MovieType.SERIAL -> cardView.context.getString(R.string.serial)
                    MovieType.CINEMA -> cardView.context.getString(R.string.cinema)
                    else -> ""
                }
            )
            siteItem.ratingLabel()?.let { add(it) }
        }.filter { it.isNotBlank() }.joinToString(" • ")

        cardView.apply {
            titleText = siteItem.title
            this.contentText = contentText
            contentDescription = listOf(titleText?.toString(), contentText)
                .filter { !it.isNullOrBlank() }
                .joinToString(", ")

            TvCardFocusHelper.applyFocusState(this, hasFocus())

            mainImageView?.loadPoster(siteItem.posterUrl, baseUrl)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        TvCardFocusHelper.reset(cardView)
        cardView.badgeImage = null
        cardView.mainImageView?.clearPoster()
        cardView.mainImage = null
    }
}
