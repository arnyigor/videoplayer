package com.arny.mobilecinema.presentation.tv.settings

import androidx.lifecycle.ViewModel
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TvSettingsViewModel(private val prefs: Prefs) : ViewModel() {
    private val _autoplay = MutableStateFlow(prefs.get<Boolean>(PrefsConstants.PREF_KEY_AUTOPLAY_NEXT) ?: true)
    val autoplay = _autoplay.asStateFlow()
    fun toggleAutoplay() {
        val enabled = !_autoplay.value
        prefs.put(PrefsConstants.PREF_KEY_AUTOPLAY_NEXT, enabled)
        _autoplay.value = enabled
    }
}
