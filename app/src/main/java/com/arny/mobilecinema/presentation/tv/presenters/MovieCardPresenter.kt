package com.arny.mobilecinema.presentation.tv.presenters

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.bumptech.glide.Glide

class MovieCardPresenter : Presenter() {

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

        cardView.apply {
            titleText = movie.title
            contentText = movie.year?.toString() ?: ""

            if (movie.img.isNotBlank()) {
                Glide.with(context)
                    .load(movie.img)
                    .centerCrop()
                    .error(R.drawable.placeholder_movie)
                    .into(mainImageView!!)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
