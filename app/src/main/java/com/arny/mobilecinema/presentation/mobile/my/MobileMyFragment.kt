package com.arny.mobilecinema.presentation.mobile.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.mobile.ui.my.MobileMyScreen
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme

/**
 * Вкладка «Моё» (MOB-08A). Разделы внутри вкладки живут собственным back stack:
 * история, избранное и настройки открываются поверх неё и возвращаются по Back.
 */
class MobileMyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MobileTheme {
                MobileMyScreen(
                    onOpenHistory = { findNavController().navigate(R.id.action_nav_my_to_nav_history) },
                    onOpenFavorites = { findNavController().navigate(R.id.action_nav_my_to_nav_favorite) },
                    onOpenSettings = { findNavController().navigate(R.id.action_nav_my_to_nav_prefs) },
                )
            }
        }
    }
}
