package com.arny.mobilecinema.presentation.tv.details

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.tv.presenters.TvCardFocusHelper
import com.bumptech.glide.Glide
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Карточка эпизода.
 *
 * [onItemClick] — собственный обработчик нажатия. В деталях Leanback теряет свой
 * OnClickListener у строки серий (строка остаётся без слушателя), и карточка
 * перестаёт реагировать на пульт. Поэтому DPAD_CENTER/ENTER ловим OnKeyListener'ом:
 * ACTION_DOWN погашаем, чтобы performClick не сработал повторно.
 */
class TvEpisodeCardPresenter(
    private val cardWidth: Int = 313,
    private val cardHeight: Int = 176,
    private val onItemClick: ((EpisodeItem?) -> Unit)? = null,
) : Presenter(), KoinComponent {

    private val prefs: Prefs by inject()

    private class EpisodeViewHolder(view: View) : ViewHolder(view) {
        var item: EpisodeItem? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(cardWidth, cardHeight)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        TvCardFocusHelper.setup(cardView)

        val holder = EpisodeViewHolder(cardView)
        val click = onItemClick
        if (click != null) {
            cardView.setOnKeyListener { _, keyCode, event ->
                val isClickKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isClickKey) {
                    false
                } else {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        // holder.item может быть уже очищен unbind'ом: экран деталей
                        // сам подставит текущий выбранный эпизод.
                        click.invoke(holder.item)
                    }
                    true
                }
            }
        }
        return holder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val episodeItem = item as? EpisodeItem ?: return
        val holder = viewHolder as? EpisodeViewHolder ?: return
        holder.item = episodeItem

        val episode = episodeItem.episode
        val cardView = viewHolder.view as ImageCardView

        val seasonNumber = episodeItem.seasonNumber
        val episodeLabel = episode.episode.ifBlank { (episodeItem.episodeIndex + 1).toString() }

        cardView.titleText = episode.title.ifBlank {
            "Серия $episodeLabel"
        }

        cardView.contentText = "Сезон $seasonNumber • Серия $episodeLabel"

        cardView.contentDescription = listOf(cardView.titleText, cardView.contentText)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        TvCardFocusHelper.applyFocusState(cardView, cardView.hasFocus())

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
        // item НЕ обнуляем: Leanback чистит ViewHolder'ы при rebind строки, а карточка
        // остаётся на экране — без сохранённого item нажатие пульта уходило в никуда.
        val cardView = viewHolder.view as ImageCardView
        TvCardFocusHelper.reset(cardView)
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
