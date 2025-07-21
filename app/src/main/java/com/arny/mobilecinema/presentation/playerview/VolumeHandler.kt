package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs

class VolumeHandler(
    private val context: Context,
    private val prefs: Prefs,
    private val audioManager: AudioManager,
    private val onVolumeChanged: (Int, Int) -> Unit // (volume, boost)
) {
    private companion object {
        const val MAX_BOOST_DEFAULT = 300
    }

    private var enhancer: LoudnessEnhancer? = null
    private var boost: Int = -1
    var volume: Int = -1
    private val maxBoost by lazy {
        prefs.get<String>(context.getString(R.string.pref_max_boost_size))?.toIntOrNull()
            ?: MAX_BOOST_DEFAULT
    }

    fun setAudioSessionId(sessionId: Int) {
        enhancer = LoudnessEnhancer(sessionId).apply {
            enabled = true
        }
    }

    private fun updateGain() {
        enhancer?.setTargetGain(boost)
    }

    fun handleVolumeChange(newVolume: Int) {
        volume = newVolume
        boost = 0
        updateGain()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        onVolumeChanged(volume, boost)
    }

    fun handleBoostChange(newBoost: Int) {
        boost = newBoost.coerceIn(0, maxBoost)
        updateGain()
        onVolumeChanged(volume, boost)
    }

    fun getCurrentVolumeWithBoost(): Float {
        return volume + boost.toFloat() / 10
    }

    fun release() {
        enhancer?.release()
    }
}