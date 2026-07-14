package com.arny.mobilecinema.presentation.tv.presenters

import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import com.arny.mobilecinema.R

object TvCardFocusHelper {

    private const val FOCUS_ANIMATION_DURATION_MS = 120L
    private const val FOCUSED_SCALE = 1.08f
    private const val DEFAULT_SCALE = 1f
    private const val FOCUSED_ELEVATION = 18f
    private const val DEFAULT_ELEVATION = 2f

    fun setup(cardView: ImageCardView) {
        cardView.foreground = ContextCompat.getDrawable(
            cardView.context,
            R.drawable.tv_card_focus_foreground
        )
        cardView.setInfoAreaBackgroundColor(
            ContextCompat.getColor(cardView.context, R.color.card_dark_bg)
        )
        cardView.setOnFocusChangeListener { _, hasFocus ->
            applyFocusState(cardView, hasFocus)
        }
        applyFocusState(cardView, cardView.hasFocus())
    }

    fun reset(cardView: ImageCardView) {
        cardView.animate().cancel()
        cardView.scaleX = DEFAULT_SCALE
        cardView.scaleY = DEFAULT_SCALE
        cardView.elevation = DEFAULT_ELEVATION
        cardView.translationZ = 0f
        cardView.setInfoAreaBackgroundColor(
            ContextCompat.getColor(cardView.context, R.color.card_dark_bg)
        )
    }

    fun applyFocusState(cardView: ImageCardView, hasFocus: Boolean) {
        cardView.animate().cancel()
        val scale = if (hasFocus) FOCUSED_SCALE else DEFAULT_SCALE
        cardView.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(FOCUS_ANIMATION_DURATION_MS)
            .start()

        val elevation = if (hasFocus) FOCUSED_ELEVATION else DEFAULT_ELEVATION
        cardView.elevation = elevation
        cardView.translationZ = if (hasFocus) FOCUSED_ELEVATION else 0f
        cardView.setInfoAreaBackgroundColor(
            ContextCompat.getColor(
                cardView.context,
                if (hasFocus) R.color.card_light_bg else R.color.card_dark_bg
            )
        )
    }
}
