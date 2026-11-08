package com.arny.mobilecinema.presentation.tv.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.settings.CatalogSyncViewModel
import com.arny.mobilecinema.presentation.settings.SyncResult
import com.arny.mobilecinema.presentation.tv.ui.components.SectionHeader
import com.arny.mobilecinema.presentation.tv.ui.components.TvFilterChip
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class TvSettingsFragment : Fragment() {
    private val sync: CatalogSyncViewModel by viewModel()
    private val settings: TvSettingsViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TvTheme {
                    val state by sync.state.collectAsStateWithLifecycle()
                    val autoplay by settings.autoplay.collectAsStateWithLifecycle()
                    val busy = state.isChecking || state.isLoading
                    val status = when {
                        state.isChecking -> getString(R.string.pref_sync_checking)
                        state.error -> getString(R.string.pref_sync_status_error)
                        state.updateDate != null -> getString(R.string.pref_sync_status_available, state.updateDate)
                        state.lastResult == SyncResult.CHECKED_NO_UPDATE -> getString(R.string.pref_sync_status_no_update)
                        else -> catalogStatusText(requireContext(), state.phase, state.progress, state.processedMovies)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(TvColors.Background),
                        contentPadding = PaddingValues(TvDimens.ScreenHorizontal, TvDimens.ScreenVertical),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item { SectionHeader(title = stringResource(R.string.tv_settings_title)) }
                        item {
                            TvFilterChip(
                                label = stringResource(if (autoplay) R.string.tv_autoplay_on else R.string.tv_autoplay_off),
                                selected = autoplay, onClick = settings::toggleAutoplay,
                            )
                        }
                        item { Text(stringResource(R.string.pref_summary_autoplay_next), color = TvColors.TextSecondary) }
                        item { Text(status, color = TvColors.OnBackground) }
                        item {
                            Button(onClick = sync::check, enabled = !busy) {
                                Text(stringResource(R.string.pref_sync_check))
                            }
                        }
                        item {
                            Button(onClick = sync::loadUpdate, enabled = !busy && state.updateDate != null) {
                                Text(stringResource(R.string.pref_sync_load))
                            }
                        }
                        item {
                            Button(onClick = {
                                AlertDialog.Builder(requireContext()).setTitle(R.string.pref_sync_full)
                                    .setMessage(R.string.tv_catalog_confirm)
                                    .setPositiveButton(android.R.string.ok) { _, _ -> sync.fullUpdate() }
                                    .setNegativeButton(android.R.string.cancel, null).show()
                            }, enabled = !busy) { Text(stringResource(R.string.pref_sync_full)) }
                        }
                        if (state.isLoading) item {
                            Button(onClick = sync::cancelUpdate) { Text(stringResource(R.string.pref_sync_cancel)) }
                        }
                    }
                }
            }
        }
}
