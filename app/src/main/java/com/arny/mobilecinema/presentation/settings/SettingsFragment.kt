package com.arny.mobilecinema.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.sources.prefs.Prefs
import com.arny.mobilecinema.data.repository.sources.prefs.PrefsConstants
import com.arny.mobilecinema.databinding.FSettingsBinding
import com.arny.mobilecinema.presentation.utils.viewBinding


class SettingsFragment : Fragment() {
    private val prefs by lazy {
        Prefs.getInstance(requireContext())
    }

    private val binding by viewBinding { FSettingsBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: FSettingsBinding) = with(binding) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.menu_preferences)
        chbCacheAvailable.setOnCheckedChangeListener { _, isChecked ->
            prefs.put(PrefsConstants.PREF_SAVE_TO_STORE, isChecked)
        }

        chbCacheAvailable.isChecked = prefs.get<Boolean>(PrefsConstants.PREF_SAVE_TO_STORE) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.f_settings, container, false)
    }
}
