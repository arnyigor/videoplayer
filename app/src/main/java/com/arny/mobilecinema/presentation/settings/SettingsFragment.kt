package com.arny.mobilecinema.presentation.settings

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.updateTitle
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
        updateTitle(getString(R.string.action_settings))

        setupDeveloperSection()
        setupCatalogSync()
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
                (preferenceScreen as PreferenceScreen).removePreference(category)
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

        // lifecycle фрагмента, а не viewLifecycleOwner: onCreatePreferences вызывается
        // до создания view, и viewLifecycleOwner там ещё не существует.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncViewModel.state.collectLatest { state ->
                    renderSyncState(state)
                }
            }
        }
    }

    private fun renderSyncState(state: CatalogSyncState) {
        val status = findPreference<Preference>(getString(R.string.pref_key_sync_status)) ?: return
        val date = state.updateDate
        status.summary = when {
            state.isChecking -> getString(R.string.pref_sync_checking)
            state.error -> getString(R.string.pref_sync_status_error)
            date != null -> getString(R.string.pref_sync_status_available, date)
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
                content = getString(R.string.internet_connection_error),
                cancelable = true,
            )
        }
    }
}
