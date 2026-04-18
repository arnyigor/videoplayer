package com.arny.mobilecinema.presentation.tv.presenters

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.ViewMovie
import com.bumptech.glide.Glide
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MovieCardPresenter : Presenter(), KoinComponent {

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
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? ViewMovie ?: return
        val cardView = viewHolder.view as ImageCardView

        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()

        cardView.apply {
            titleText = movie.title
            contentText = if (movie.year > 0) movie.year.toString() else ""

            if (movie.img.isNotBlank()) {
                val fullUrl = movie.img.getWithDomain(baseUrl)
                Glide.with(context)
                    .load(fullUrl)
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
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}