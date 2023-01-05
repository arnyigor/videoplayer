package com.arny.mobilecinema.presentation.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.utils.updateTitle

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        updateTitle(getString(R.string.action_settings))
    }
}
