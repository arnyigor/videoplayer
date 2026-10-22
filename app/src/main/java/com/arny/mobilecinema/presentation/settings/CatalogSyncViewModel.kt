package com.arny.mobilecinema.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Состояние служебных операций с каталогом (SYNC-01).
 *
 * Модель аудита: `check` (без записи в базу) → `preview` (диалог с датой и объёмом) →
 * `apply` (идемпотентная загрузка) → `report` (последний результат в настройках).
 * Три операции сведены в одну точку учёта, чтобы не показывать пользователю три
 * разных входа с неясной разницей (аудит v2, §5 и §5.2).
 */
data class CatalogSyncState(
    val isChecking: Boolean = false,
    val isLoading: Boolean = false,
    /** Дата доступного обновления; null — обновления нет или ещё не проверяли. */
    val updateDate: String? = null,
    /** У сайта есть частичное обновление — можно загрузить его вместо полного пакета. */
    val hasPartUpdate: Boolean = false,
    val error: Boolean = false,
    /** Результат последней операции для строки статуса. */
    val lastResult: SyncResult = SyncResult.NONE,
)

enum class SyncResult {
    NONE,
    CHECKED_NO_UPDATE,
    UPDATE_AVAILABLE,
    LOADING_STARTED,
    /** Загрузка идёт или уже поставлена в очередь — состояние неизвестно точно. */
    LOADING_IN_PROGRESS,
    FAILED,
}

class CatalogSyncViewModel(
    private val interactor: DataUpdateInteractor,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogSyncState())
    val state = _state.asStateFlow()

    /** Проверка без мутаций каталога: читается только файл даты обновления. */
    fun check() {
        if (_state.value.isChecking || _state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isChecking = true, error = false) }
            interactor.getUpdateDate(force = true)
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            val date = result.result.updateDateTime
                            _state.update {
                                it.copy(
                                    isChecking = false,
                                    updateDate = date.takeIf { value -> value.isNotBlank() },
                                    hasPartUpdate = result.result.hasPartUpdate,
                                    lastResult = if (date.isBlank()) {
                                        SyncResult.CHECKED_NO_UPDATE
                                    } else {
                                        SyncResult.UPDATE_AVAILABLE
                                    },
                                )
                            }
                        }

                        is DataResult.Error -> {
                            Timber.e(result.throwable, "catalog check failed")
                            _state.update {
                                it.copy(isChecking = false, error = true, lastResult = SyncResult.FAILED)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Загрузка найденного обновления. Повторный вызов безопасен: сервис идемпотентно
     * обновляет записи каталога, дубликатов не создаёт.
     */
    fun loadUpdate() {
        val state = _state.value
        if (state.isLoading || state.updateDate == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = false, lastResult = SyncResult.LOADING_STARTED) }
            runCatching {
                interactor.requestFile(force = false, hasPartUpdate = state.hasPartUpdate)
            }.onFailure { error ->
                Timber.e(error, "catalog load failed")
                _state.update { it.copy(isLoading = false, error = true, lastResult = SyncResult.FAILED) }
                return@launch
            }
            _state.update { it.copy(isLoading = false, lastResult = SyncResult.LOADING_IN_PROGRESS) }
        }
    }

    /** Полное обновление обходом сайта: дороже по времени, перезаписывает ссылки. */
    fun fullUpdate() {
        if (_state.value.isLoading) return
        interactor.updateAll()
        _state.update { it.copy(lastResult = SyncResult.LOADING_IN_PROGRESS, updateDate = null) }
    }

    fun cancelUpdate() {
        interactor.cancelUpdate()
        _state.update { it.copy(isLoading = false, lastResult = SyncResult.NONE) }
    }
}
