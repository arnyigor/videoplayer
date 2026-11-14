package com.arny.mobilecinema.presentation.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.EditTextPreference
import androidx.preference.SwitchPreferenceCompat
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.toast
import java.text.NumberFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

/**
 * Настройки. Пользовательские разделы — воспроизведение и данные; переходные флаги
 * интерфейса и технические экраны живут в «Инструментах разработчика», которых нет
 * в release (DBG-01, аудит v2 §6).
 *
 * Служебные операции каталога сведены в раздел «Каталог и обновления»: проверка без
 * изменений → подтверждение с датой → загрузка → результат в строке статуса (SYNC-01).
 */
class SettingsFragment : PreferenceFragmentCompat(), KoinComponent {

    private val syncViewModel: CatalogSyncViewModel by viewModel()

    /** Последняя дата, для которой уже показали диалог: не повторяем один и тот же вопрос. */
    private var shownUpdateDate: String? = null
    private var shownNoUpdate = false
    private var shownError = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        setupDeveloperSection()
        setupPlaybackPreferences()
        setupCatalogSync()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.f_settings, container, false)
        root.findViewById<ViewGroup>(R.id.settings_content)
            .addView(super.onCreateView(inflater, container, savedInstanceState))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncViewModel.state.collectLatest { renderSyncState(it) }
            }
        }
    }

    private fun setupPlaybackPreferences() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.pref_key_pip_mode))?.let { pip ->
            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            pip.isEnabled = supported
            if (!supported) pip.setSummary(R.string.pref_pip_unavailable)
        }
        findPreference<EditTextPreference>(getString(R.string.pref_max_boost_size))?.let { boost ->
            // Preserve the existing storage unit/range; LoudnessEnhancer consumes millibels.
            if (parseVolumeBoost(boost.text) == null) boost.text = "1000"
            boost.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
                val value = parseVolumeBoost(preference.text) ?: 1000
                getString(R.string.pref_boost_summary, value,
                    NumberFormat.getNumberInstance().format(value / 100.0))
            }
            boost.setOnBindEditTextListener { edit ->
                edit.inputType = InputType.TYPE_CLASS_NUMBER
                edit.setSingleLine()
                edit.hint = getString(R.string.pref_boost_range)
                edit.selectAll()
            }
            boost.setOnPreferenceChangeListener { _, value ->
                val valid = parseVolumeBoost(value as? String) != null
                if (!valid) toast(getString(R.string.pref_boost_range))
                valid
            }
        }
    }

    private fun setupDeveloperSection() {
        val developerCategory = findPreference<Preference>(getString(R.string.pref_key_developer_category))
        if (BuildConfig.DEBUG) {
            findPreference<Preference>(getString(R.string.pref_key_developer_compose_test))
                ?.setOnPreferenceClickListener {
                    startActivity(
                        Intent().setClassName(
                            requireContext(),
                            "com.arny.mobilecinema.presentation.compose.ComposeTestActivity"
                        )
                    )
                    true
                }
        } else {
            // В release технических пунктов быть не должно вовсе, а не просто скрытых.
            developerCategory?.let { category ->
                preferenceScreen.removePreference(category)
            }
        }
    }

    private fun setupCatalogSync() {
        findPreference<Preference>(getString(R.string.pref_key_sync_check))
            ?.setOnPreferenceClickListener {
                shownUpdateDate = null
                shownNoUpdate = false
                syncViewModel.check()
                true
            }
        findPreference<Preference>(getString(R.string.pref_key_sync_load))
            ?.setOnPreferenceClickListener {
                if (syncViewModel.state.value.updateDate == null) {
                    toast(getString(R.string.pref_sync_load_summary))
                } else {
                    syncViewModel.loadUpdate()
                    toast(getString(R.string.pref_sync_status_started))
                }
                true
            }
        findPreference<Preference>(getString(R.string.pref_key_sync_full))
            ?.setOnPreferenceClickListener {
                alertDialog(
                    title = getString(R.string.full_update_title),
                    content = getString(R.string.question_update_all),
                    btnOkText = getString(android.R.string.ok),
                    btnCancelText = getString(android.R.string.cancel),
                    onConfirm = {
                        syncViewModel.fullUpdate()
                        toast(getString(R.string.pref_sync_status_started))
                    },
                )
                true
            }

        findPreference<Preference>(getString(R.string.pref_key_sync_cancel))
            ?.setOnPreferenceClickListener {
                syncViewModel.cancelUpdate()
                true
            }
    }

    private fun renderSyncState(state: CatalogSyncState) {
        val status = findPreference<Preference>(getString(R.string.pref_key_sync_status)) ?: return
        val date = state.updateDate
        val busy = state.isChecking || state.isLoading
        findPreference<Preference>(getString(R.string.pref_key_sync_check))?.isEnabled = !busy
        findPreference<Preference>(getString(R.string.pref_key_sync_load))?.isEnabled = !busy && date != null
        findPreference<Preference>(getString(R.string.pref_key_sync_full))?.isEnabled = !busy
        findPreference<Preference>(getString(R.string.pref_key_sync_cancel))?.isVisible = state.isLoading
        status.summary = when {
            state.phase == CatalogUpdatePhase.INTERRUPTED -> getString(R.string.catalog_update_interrupted)
            state.isLoading -> {
                val stage = getString(when (state.phase) {
                    CatalogUpdatePhase.DOWNLOADING -> R.string.pref_sync_downloading
                    CatalogUpdatePhase.UNPACKING -> R.string.pref_sync_unpacking
                    CatalogUpdatePhase.IMPORTING -> R.string.pref_sync_importing
                    CatalogUpdatePhase.SCRAPING -> R.string.pref_sync_scraping
                    else -> R.string.pref_sync_status_started
                })
                state.progress?.let { getString(R.string.pref_sync_progress, stage, it) } ?: stage
            }
            state.isChecking -> getString(R.string.pref_sync_checking)
            state.error -> getString(R.string.pref_sync_status_error)
            date != null -> getString(R.string.pref_sync_status_available, date)
            state.lastResult == SyncResult.SUCCEEDED -> state.processedMovies?.let {
                getString(R.string.pref_sync_success_count, it)
            } ?: getString(R.string.pref_sync_success)
            state.lastResult == SyncResult.CANCELLED -> getString(R.string.pref_sync_cancelled)
            state.lastResult == SyncResult.CHECKED_NO_UPDATE -> getString(R.string.pref_sync_status_no_update)
            state.lastResult == SyncResult.LOADING_STARTED ||
                    state.lastResult == SyncResult.LOADING_IN_PROGRESS ->
                getString(R.string.pref_sync_status_started)

            else -> getString(R.string.pref_sync_status_never)
        }

        // Preview: показываем вопрос один раз на найденную дату.
        if (date != null && shownUpdateDate != date) {
            shownUpdateDate = date
            alertDialog(
                title = getString(R.string.new_films_update),
                content = getString(R.string.question_update_format, date),
                btnOkText = getString(android.R.string.ok),
                btnCancelText = getString(android.R.string.cancel),
                btnNeutralText = getString(R.string.full_update),
                cancelable = true,
                onConfirm = { syncViewModel.loadUpdate() },
                onNeutral = { syncViewModel.fullUpdate() },
            )
        }
        if (state.lastResult == SyncResult.CHECKED_NO_UPDATE && !shownNoUpdate) {
            shownNoUpdate = true
            alertDialog(
                title = getString(R.string.new_films_update_not_found_title),
                content = getString(R.string.new_films_update_not_found_content),
                cancelable = true,
            )
        }
        if (!state.error) {
            shownError = false
        }
        if (state.error && !shownError) {
            shownError = true
            alertDialog(
                title = getString(R.string.error_loading_data),
                content = getString(R.string.pref_sync_status_error),
                cancelable = true,
            )
        }
    }
}

/** Existing millibel range used by the player; reject invalid/overflowing input before saving. */
internal fun parseVolumeBoost(value: String?): Int? =
    value?.toIntOrNull()?.takeIf { it in 100..100000 }
