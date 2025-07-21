package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler

class SettingsContentObserver(
    context: Context, handler: Handler,
    val onChange: (volume: Int) -> Unit
) : ContentObserver(handler) {
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE)
            as AudioManager

    override fun deliverSelfNotifications(): Boolean = false
    override fun onChange(selfChange: Boolean) {
        onChange(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
}