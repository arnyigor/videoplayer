package com.arny.mobilecinema.presentation.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.CacheChangeType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.BufferedSharedFlow
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModel(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _alert = BufferedChannel<Alert>()
    val alert: Flow<Alert> = _alert.receiveAsFlow()

    private val _urlData = BufferedSharedFlow<String>()
    val urlData: Flow<String> = _urlData.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading: Flow<Boolean> = _loading.asStateFlow()

    private val _sortCategory = MutableStateFlow(MovieSortCategory.NEW)

    private val _refreshTrigger = MutableStateFlow(0L)
    private val _historyTrigger = MutableStateFlow(System.currentTimeMillis())

    val moviesDataFlow: Flow<PagingData<ViewMovie>> = combine(
        _refreshTrigger,
        _sortCategory
    ) { _, sort ->
        sort
    }
        .flatMapLatest { sortCategory ->
            moviesInteractor.getMovies(
                search = "",
                order = sortCategory.order,
                searchType = "",
                searchAddTypes = listOf(
                    AppConstants.SearchType.CINEMA,
                    AppConstants.SearchType.SERIAL
                )
            )
        }
        .cachedIn(viewModelScope)

    val favoriteMoviesFlow: Flow<PagingData<ViewMovie>> = _refreshTrigger
        .flatMapLatest { _ ->
            moviesInteractor.getFavoriteMovies(
                search = "",
                order = "",
                searchType = ""
            )
        }

    val historyMoviesFlow: Flow<PagingData<ViewMovie>> = _historyTrigger
        .flatMapLatest { _ ->
            historyInteractor.getHistoryMovies(
                search = "",
                order = AppConstants.Order.LAST_TIME,
                searchType = ""
            )
        }
        .cachedIn(viewModelScope)

    init {
        checkForUpdate()
        observeHistoryChanges()
    }

    private fun observeHistoryChanges() {
        viewModelScope.launch {
            historyInteractor.cacheChange.collect { change ->
                if (change == CacheChangeType.CACHE || change == CacheChangeType.SERIAL_POSITION) {
                    reloadHistory()
                    historyInteractor.setCacheChanged(false)
                }
            }
        }
    }

    fun refreshData() {
        _refreshTrigger.value++
    }

    fun reloadHistory() {
        _historyTrigger.value = System.currentTimeMillis()
    }

    fun setSortCategory(category: MovieSortCategory) {
        if (_sortCategory.value != category) {
            _sortCategory.value = category
            _refreshTrigger.value++
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyInteractor.clearAllViewHistory().collectLatest { result ->
                when (result) {
                    is DataResult.Success -> reloadHistory()
                    is DataResult.Error -> {}
                }
            }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(false)
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {
                        }

                        is DataResult.Success -> {
                            val updateTime = result.result.updateDateTime
                            if (updateTime.isNotBlank()) {
                                _updateAvailable.value = true
                            }
                        }
                    }
                }
        }
    }

    fun downloadData(force: Boolean = false) {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(force)
                .onStart {
                    _updateAvailable.value = false
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                        }

                        is DataResult.Success -> {
                            val updateResult = result.result
                            val hasPartUpdate = updateResult.hasPartUpdate
                            val updateTime = updateResult.updateDateTime

                            if (updateTime.isNotBlank() && !updateTime.contains("""[/|\\]""".toRegex())) {
                                _alert.trySend(
                                    Alert(
                                        title = ResourceString(R.string.new_films_update),
                                        content = ResourceString(
                                            R.string.question_update_format,
                                            updateTime
                                        ),
                                        btnOk = ResourceString(android.R.string.ok),
                                        btnCancel = ResourceString(android.R.string.cancel),
                                        btnNeutral = if (hasPartUpdate) {
                                            ResourceString(R.string.full_update)
                                        } else {
                                            null
                                        },
                                        type = AlertType.Update(false, hasPartUpdate)
                                    )
                                )
                            } else {
                                _updateAvailable.value = false
                                _alert.trySend(
                                    Alert(
                                        title = ResourceString(R.string.new_films_update_not_found_title),
                                        content = ResourceString(R.string.new_films_update_not_found_content),
                                        btnOk = ResourceString(android.R.string.ok),
                                        type = AlertType.SimpleAlert
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun checkIntent() {
        viewModelScope.launch {
            dataUpdateInteractor.intentUrl()
                .collect { url ->
                    _urlData.emit(url)
                }
        }
    }

    /**
     * Запуск обновления после подтверждения пользователем на TV.
     * Пропускает второй alert и сразу запускает update-flow.
     */
    fun startUpdateAfterUserConfirmation(force: Boolean = false) {
        viewModelScope.launch {
            _loading.value = true

            dataUpdateInteractor.getUpdateDate(force)
                .catch { e ->
                    e.printStackTrace()
                    _loading.value = false
                }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _loading.value = false
                        }

                        is DataResult.Success -> {
                            val hasPartUpdate = result.result.hasPartUpdate
                            dataUpdateInteractor.requestFile(force, hasPartUpdate)
                            checkIntent()
                            _loading.value = false
                        }
                    }
                }
        }
    }

    fun stopUpdate() {
        dataUpdateInteractor.cancelUpdate()
    }
}
