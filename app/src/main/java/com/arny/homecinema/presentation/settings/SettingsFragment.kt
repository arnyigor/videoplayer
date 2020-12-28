package com.arny.homecinema.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.arny.homecinema.R
import com.arny.homecinema.data.repository.sources.Prefs
import com.arny.homecinema.data.repository.sources.PrefsConstants
import com.arny.homecinema.databinding.FSettingsBinding
import com.arny.homecinema.presentation.utils.viewBinding


class SettingsFragment : Fragment() {
    private val prefs by lazy {
        Prefs.getInstance(requireContext())
    }

    private val binding by viewBinding { FSettingsBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: FSettingsBinding) = with(binding) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.menu_preferences)
        chbCacheAvailable.setOnCheckedChangeListener { _, isChecked ->
            prefs.put(PrefsConstants.PREF_CACHE_VIDEO, isChecked)
        }

        chbCacheAvailable.isChecked = prefs.get<Boolean>(PrefsConstants.PREF_CACHE_VIDEO) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.f_settings, container, false)
    }
}
