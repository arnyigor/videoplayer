package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.arny.mobilecinema.R

/**
 * Вертикальный слайдер для управления яркостью/громкостью.
 * Плавное изменение значения при вертикальном свайпе.
 */
class VerticalSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint объекты
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
        alpha = 100
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorAccent)
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 2f, 0x40000000)
    }

    // Размеры
    private var trackWidth = 8f * resources.displayMetrics.density
    private var thumbRadius = 14f * resources.displayMetrics.density
    private var cornerRadius = 4f * resources.displayMetrics.density
    
    // Состояние
    private var progress: Int = 0  // 0-maxProgress
    private var maxProgress = 100
    private var minProgress = 0
    
    // Touch tracking
    private var lastTouchY = 0f
    private var isDragging = false
    
    // Callbacks
    var onProgressChanged: ((Int) -> Unit)? = null
    var onProgressChangeFinished: (() -> Unit)? = null

    // Rect для отрисовки
    private val trackRect = RectF()
    private val progressRect = RectF()

    init {
        // Включаем software layer для shadow
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (thumbRadius * 2 + paddingLeft + paddingRight).toInt()
        val desiredHeight = 200 * resources.displayMetrics.density.toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val trackLeft = centerX - trackWidth / 2
        val trackRight = centerX + trackWidth / 2
        
        // Track (фоновая линия)
        trackRect.set(
            trackLeft,
            thumbRadius,
            trackRight,
            height - thumbRadius
        )
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)
        
        // Progress (заполненная часть)
        val progressHeight = (height - 2 * thumbRadius) * progress / maxProgress.toFloat()
        val progressTop = height - thumbRadius - progressHeight
        progressRect.set(
            trackLeft,
            progressTop,
            trackRight,
            height - thumbRadius
        )
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)
        
        // Thumb (кругляшок)
        val thumbY = height - thumbRadius - progressHeight
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = lastTouchY - event.y  // Инвертируем: вверх = увеличить
                    val deltaProgress = (deltaY / (height - 2 * thumbRadius) * maxProgress).toInt()
                    
                    if (deltaProgress != 0) {
                        val newProgress = (progress + deltaProgress).coerceIn(minProgress, maxProgress)
                        if (newProgress != progress) {
                            progress = newProgress
                            onProgressChanged?.invoke(progress)
                            invalidate()
                        }
                        lastTouchY = event.y
                    }
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onProgressChangeFinished?.invoke()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setProgress(value: Int, animate: Boolean = false) {
        progress = value.coerceIn(minProgress, maxProgress)
        invalidate()
    }

    fun getProgress(): Int = progress

    fun setMaxProgress(max: Int) {
        maxProgress = max
        invalidate()
    }
}
