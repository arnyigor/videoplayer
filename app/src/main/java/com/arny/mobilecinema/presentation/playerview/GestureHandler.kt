package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class GestureHandler(
    private val context: Context,
    private val onVolumeChange: (Float) -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onSeekPlayback: (Float) -> Unit,
    private val onScroll: (Float, Float) -> Unit = { _, _ -> },
) : GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private val gestureDetector = GestureDetectorCompat(context, this)
    private val minScrollDistance = 16.dpToPx(context)
    private var isScrolling = false

    // Константы для определения направления
    private companion object {
        const val HORIZONTAL_ANGLE_THRESHOLD = 30 // градусов
        const val VERTICAL_ANGLE_THRESHOLD = 60 // градусов
        const val MIN_SCROLL_DISTANCE = 50 // пикселей
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrolling = false
            }
        }
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false

    override fun onDoubleTap(e: MotionEvent): Boolean = false

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (!isScrolling) {
            isScrolling = true
            return handleInitialScroll(e1, e2)
        }

        val dx = e2.x - (e1?.x ?: 0f)
        val dy = e2.y - (e1?.y ?: 0f)

        // Определяем угол жеста
        val angle = Math.toDegrees(atan2(abs(dy), abs(dx)).toDouble())
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < minScrollDistance) return false

        when {
            angle < HORIZONTAL_ANGLE_THRESHOLD -> {
                // Преимущественно горизонтальный скролл
                handleHorizontalScroll(dx)
            }
            angle > VERTICAL_ANGLE_THRESHOLD -> {
                // Преимущественно вертикальный скролл
                handleVerticalScroll(e2.x, dy)
            }
            else -> {
                // Диагональный скролл - обрабатываем оба направления
                handleHorizontalScroll(dx)
                handleVerticalScroll(e2.x, dy)
            }
        }

        onScroll.invoke(dx, dy)
        return true
    }

    override fun onLongPress(p0: MotionEvent) {

    }

    private fun handleInitialScroll(e1: MotionEvent?, e2: MotionEvent): Boolean {
        // Дополнительная логика инициализации при начале скролла
        return true
    }

    private fun handleHorizontalScroll(deltaX: Float) {
        val seekDelta = (deltaX / context.resources.displayMetrics.widthPixels) * 10000
        onSeekPlayback(seekDelta)
    }

    private fun handleVerticalScroll(xPos: Float, deltaY: Float) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        when {
            xPos < screenWidth * 0.3 -> {
                // Левая часть экрана - яркость
                val brightnessDelta = (deltaY / context.resources.displayMetrics.heightPixels) * 100
                onBrightnessChange(brightnessDelta)
            }
            xPos > screenWidth * 0.7 -> {
                // Правая часть экрана - громкость
                val volumeDelta = (deltaY / context.resources.displayMetrics.heightPixels) * 100
                onVolumeChange(volumeDelta)
            }
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // Обработка быстрых свайпов
        val distanceX = e2.x - (e1?.x ?: 0f)
        val distanceY = e2.y - (e1?.y ?: 0f)

        if (abs(distanceX) > MIN_SCROLL_DISTANCE || abs(distanceY) > MIN_SCROLL_DISTANCE) {
            if (abs(distanceX) > abs(distanceY)) {
                handleHorizontalScroll(distanceX * 2)
            } else {
                handleVerticalScroll(e2.x, distanceY * 2)
            }
            return true
        }
        return false
    }

    fun release() {
        // Очистка ресурсов
    }

    private fun Int.dpToPx(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }
}