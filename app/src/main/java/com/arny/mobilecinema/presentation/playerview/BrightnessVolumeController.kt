package com.arny.mobilecinema.presentation.playerview

import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.arny.mobilecinema.R

/**
 * Helper для управления brightness/volume слайдерами в плеере.
 *
 * Концепция громкости:
 * - Слайдер: 0-200 (расширенный диапазон)
 * - 0-100: громкость 0-maxVolume
 * - 100-200: boost 0-maxBoost мБ
 */
class BrightnessVolumeController(
    private val rootView: View,
    private val window: Window,
    private val maxBoost: Int,
    private val onVolumeChanged: (Int) -> Unit,
    private val onBoostChanged: (Int) -> Unit
) {

    private val overlay: View = rootView.findViewById(R.id.brightnessVolumeOverlay)
    private val brightnessPanel: View = rootView.findViewById(R.id.brightnessPanel)
    private val volumePanel: View = rootView.findViewById(R.id.volumePanel)

    private val brightnessIcon: ImageView = brightnessPanel.findViewById(R.id.ivIcon)
    private val brightnessSlider: VerticalSliderView = brightnessPanel.findViewById(R.id.sliderView)
    private val brightnessValue: TextView = brightnessPanel.findViewById(R.id.tvValue)

    private val volumeIcon: ImageView = volumePanel.findViewById(R.id.ivIcon)
    private val volumeSlider: VerticalSliderView = volumePanel.findViewById(R.id.sliderView)
    private val volumeValue: TextView = volumePanel.findViewById(R.id.tvValue)
    private val volumeBoost: TextView = volumePanel.findViewById(R.id.tvBoost)

    private var hideRunnable: Runnable? = null
    private val hideDelayMs = 2000L

    // Системные значения
    private var currentVolumeAbsolute: Int = 0
    private var maxVolumeAbsolute: Int = 15
    private var currentBoostMb: Int = 0
    private var maxVolumeRange: Int = 100  // Для слайдера: 0-100 = volume, 100-200 = boost

    // Флаг для предотвращения рекурсии
    private var isProgrammaticChange: Boolean = false

    init {
        setupBrightnessSlider()
        setupVolumeSlider()
    }

    private fun setupBrightnessSlider() {
        brightnessIcon.setImageResource(R.drawable.ic_brightness)

        brightnessSlider.onProgressChanged = { progress ->
            if (!isProgrammaticChange) {
                brightnessValue.text = "$progress%"
                // Конвертируем 0-100% → 0.01-1.0 для Window
                val brightnessFloat = (progress / 100f).coerceIn(0.01f, 1.0f)
                setScreenBrightnessInternal(brightnessFloat)
                resetHideTimer()
            }
        }

        brightnessSlider.onProgressChangeFinished = {
            resetHideTimer()
        }
    }

    private fun setupVolumeSlider() {
        volumeIcon.setImageResource(R.drawable.ic_volume)

        // Устанавливаем расширенный максимум: 0-200 (100 volume + 100 boost)
        volumeSlider.setMaxProgress(200)

        volumeSlider.onProgressChanged = { progress ->
            if (!isProgrammaticChange) {
                handleVolumeProgress(progress)
                resetHideTimer()
            }
        }

        volumeSlider.onProgressChangeFinished = {
            resetHideTimer()
        }
    }

    /**
     * Обработка движения слайдера громкости.
     * progress: 0-200
     * 0-100 = volume (0-maxVolume)
     * 100-200 = boost (0-maxBoost мБ)
     */
    private fun handleVolumeProgress(progress: Int) {
        val volumePercent: Int
        val newBoostMb: Int

        if (progress <= 100) {
            // Зона громкости: 0-100% → 0-maxVolume
            volumePercent = progress
            newBoostMb = 0
        } else {
            // Зона boost: 100-200%
            volumePercent = 100
            // Маппим 100-200 → 0-maxBoost
            val boostPercent = progress - 100
            newBoostMb = (boostPercent * maxBoost / 100).coerceIn(0, maxBoost)
        }

        // Абсолютная громкость
        val newVolumeAbsolute =
            (volumePercent * maxVolumeAbsolute / 100).coerceIn(0, maxVolumeAbsolute)

        // Визуальный процент - всегда показываем 0-100 для громкости
        val displayPercent = progress.coerceAtMost(100)
        volumeValue.text = buildString {
            append(displayPercent)
            append("%")
        }

        // Обновляем только если изменилось
        val volumeChanged = newVolumeAbsolute != currentVolumeAbsolute
        val boostChanged = newBoostMb != currentBoostMb

        currentVolumeAbsolute = newVolumeAbsolute
        currentBoostMb = newBoostMb

        updateBoostUI()

        if (volumeChanged) {
            onVolumeChanged(currentVolumeAbsolute)
        }
        if (boostChanged) {
            onBoostChanged(currentBoostMb)
        }
    }

    private fun updateBoostUI() {
        if (currentBoostMb > 0) {
            volumeBoost.visibility = View.VISIBLE
            val boostPercent = (currentBoostMb * 100 / maxBoost).coerceIn(0, 100)
            volumeBoost.text = "+$boostPercent%"
        } else {
            volumeBoost.visibility = View.GONE
        }
    }

    private fun setScreenBrightnessInternal(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value.coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }

    // ─── Публичные методы ───────────────────────────────────────────────────────────

    fun initializeBrightness(brightnessFloat: Float) {
        val clamped = brightnessFloat.coerceIn(0.01f, 1.0f)
        val percent = (clamped * 100).toInt().coerceIn(1, 100)

        isProgrammaticChange = true
        try {
            brightnessSlider.setProgress(percent)
            brightnessValue.text = "$percent%"
        } finally {
            isProgrammaticChange = false
        }
    }

    fun initializeVolume(volume: Int, maxVolume: Int) {
        maxVolumeAbsolute = maxVolume.coerceAtLeast(1)
        currentVolumeAbsolute = volume.coerceIn(0, maxVolume)

        // 0-100 для volume в слайдере
        val sliderPercent = (volume * 100 / maxVolumeAbsolute).coerceIn(0, 100)

        isProgrammaticChange = true
        try {
            volumeSlider.setMaxProgress(200)  // Расширенный диапазон
            volumeSlider.setProgress(sliderPercent)
            volumeValue.text = "$sliderPercent%"
        } finally {
            isProgrammaticChange = false
        }
    }

    fun updateVolumeExternal(volume: Int, maxVolume: Int) {
        maxVolumeAbsolute = maxVolume.coerceAtLeast(1)
        currentVolumeAbsolute = volume.coerceIn(0, maxVolume)

        val sliderPercent = (volume * 100 / maxVolumeAbsolute).coerceIn(0, 100)

        isProgrammaticChange = true
        try {
            volumeSlider.setProgress(sliderPercent)
            volumeValue.text = "$sliderPercent%"
        } finally {
            isProgrammaticChange = false
        }

        // Сбрасываем boost если громкость снизилась
        if (volume < maxVolume && currentBoostMb > 0) {
            currentBoostMb = 0
            updateBoostUI()
            onBoostChanged(0)
        }
    }

    fun initializeBoost(boostMb: Int) {
        currentBoostMb = boostMb.coerceIn(0, maxBoost)

        if (boostMb > 0 && currentVolumeAbsolute >= maxVolumeAbsolute) {
            // Восстанавливаем позицию с boost
            val boostPercent = (boostMb * 100 / maxBoost).coerceIn(0, 100)
            val sliderProgress = (100 + boostPercent).coerceIn(100, 200)

            isProgrammaticChange = true
            try {
                volumeSlider.setProgress(sliderProgress)
            } finally {
                isProgrammaticChange = false
            }
        }

        updateBoostUI()
    }

    // ─── Видимость ────────────────────────────────────────────────────────────

    fun show() {
        overlay.visibility = View.VISIBLE
        brightnessPanel.visibility = View.VISIBLE
        volumePanel.visibility = View.VISIBLE
        animateIn()
        scheduleHide()
    }

    fun showBrightness() {
        overlay.visibility = View.VISIBLE
        brightnessPanel.visibility = View.VISIBLE
        volumePanel.visibility = View.GONE
        animateIn()
        scheduleHide()
    }

    fun showVolume() {
        overlay.visibility = View.VISIBLE
        brightnessPanel.visibility = View.GONE
        volumePanel.visibility = View.VISIBLE
        animateIn()
        scheduleHide()
    }

    private fun animateIn() {
        overlay.alpha = 0f
        overlay.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    fun hide() {
        hideRunnable?.let { overlay.removeCallbacks(it) }
        hideRunnable = null

        overlay.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                overlay.visibility = View.GONE
            }
            .start()
    }

    private fun scheduleHide() {
        hideRunnable?.let { overlay.removeCallbacks(it) }
        hideRunnable = Runnable { hide() }.also {
            overlay.postDelayed(it, hideDelayMs)
        }
    }

    fun resetHideTimer() {
        scheduleHide()
    }

    val brightnessSliderView: VerticalSliderView get() = brightnessSlider
    val volumeSliderView: VerticalSliderView get() = volumeSlider
}