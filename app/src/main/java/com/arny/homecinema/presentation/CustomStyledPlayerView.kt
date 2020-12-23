package com.arny.homecinema.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

import com.google.android.exoplayer2.ui.PlayerControlView

import com.google.android.exoplayer2.ui.PlayerView
import kotlin.math.abs


class CustomStyledPlayerView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    PlayerView(context, attrs, defStyleAttr), PlayerControlView.VisibilityListener {
    private var controllerVisible = false
    private var tapStartTimeMs: Long = 0
    private var tapPositionX = 0f
    private var tapPositionY = 0f

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapStartTimeMs = SystemClock.elapsedRealtime()
                tapPositionX = ev.x
                tapPositionY = ev.y
            }
            MotionEvent.ACTION_MOVE -> if (tapStartTimeMs != 0L && (abs(ev.x - tapPositionX) > DRAG_THRESHOLD
                        || abs(ev.y - tapPositionY) > DRAG_THRESHOLD)
            ) {
                tapStartTimeMs = 0
            }
            MotionEvent.ACTION_UP -> if (tapStartTimeMs != 0L) {
                if (SystemClock.elapsedRealtime() - tapStartTimeMs < LONG_PRESS_THRESHOLD_MS) {
                    if (!controllerVisible) {
                        showController()
                    } else if (controllerHideOnTouch) {
                        hideController()
                    }
                }
                tapStartTimeMs = 0
            }
        }
        return true
    }

    override fun onVisibilityChange(visibility: Int) {
        controllerVisible = visibility == View.VISIBLE
    }

    companion object {
        private const val DRAG_THRESHOLD = 10f
        private const val LONG_PRESS_THRESHOLD_MS: Long = 500
    }

    init {
        setControllerVisibilityListener(this)
    }
}