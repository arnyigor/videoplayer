package com.arny.mobilecinema.presentation.playerview

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.arny.mobilecinema.R

class SeekOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val leftContainer: View
    private val rightContainer: View
    private val leftText: TextView
    private val rightText: TextView
    private val leftArrows: ImageView
    private val rightArrows: ImageView

    private var seekSeconds = 0
    private var currentAnimator: ValueAnimator? = null
    private var isForward = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_seek_overlay, this, true)
        leftContainer = findViewById(R.id.leftSeekContainer)
        rightContainer = findViewById(R.id.rightSeekContainer)
        leftText = findViewById(R.id.tvSeekLeft)
        rightText = findViewById(R.id.tvSeekRight)
        leftArrows = findViewById(R.id.ivArrowsLeft)
        rightArrows = findViewById(R.id.ivArrowsRight)
        visibility = INVISIBLE
    }

    fun showForward(seconds: Int) {
        isForward = true
        seekSeconds += seconds
        rightText.text = context.getString(R.string.seek_seconds, seekSeconds)
        leftContainer.isVisible = false
        rightContainer.isVisible = true
        animateArrows(rightArrows, true)
        showWithAnimation()
    }

    fun showRewind(seconds: Int) {
        isForward = false
        seekSeconds += seconds
        leftText.text = context.getString(R.string.seek_seconds, seekSeconds)
        rightContainer.isVisible = false
        leftContainer.isVisible = true
        animateArrows(leftArrows, false)
        showWithAnimation()
    }

    fun reset() {
        seekSeconds = 0
        currentAnimator?.cancel()
        visibility = INVISIBLE
    }

    private fun showWithAnimation() {
        visibility = VISIBLE
        alpha = 1f
    }

    fun hideWithAnimation() {
        animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                visibility = INVISIBLE
                seekSeconds = 0
            }
            .start()
    }

    private fun animateArrows(imageView: ImageView, forward: Boolean) {
        val start = if (forward) -20f else 20f
        val end = 0f
        currentAnimator?.cancel()
        currentAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 200
            addUpdateListener {
                imageView.translationX = it.animatedValue as Float
            }
            start()
        }
    }
}
