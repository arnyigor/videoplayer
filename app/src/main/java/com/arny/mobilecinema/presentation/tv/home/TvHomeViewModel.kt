package com.arny.mobilecinema.presentation.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.models.CacheChangeType
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModel(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _alert = BufferedChannel<Alert>()
    val alert: Flow<Alert> = _alert.receiveAsFlow()

    private val _urlData = BufferedSharedFlow<String>()
    val urlData: Flow<String> = _urlData.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading: Flow<Boolean> = _loading.asStateFlow()

    private val _sortCategory = MutableStateFlow(MovieSortCategory.NEW)
    val sortCategory = _sortCategory.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    val moviesDataFlow: Flow<PagingData<ViewMovie>> = combine(
        _refreshTrigger,
        _sortCategory
    ) { refresh, sort ->
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
        .cachedIn(viewModelScope)

    val historyMoviesFlow: Flow<PagingData<ViewMovie>> = _refreshTrigger
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
            historyInteractor.cacheChange.collectLatest { change ->
                if (change == CacheChangeType.CACHE || change == CacheChangeType.SERIAL_POSITION) {
                    _refreshTrigger.value++
                    historyInteractor.setCacheChanged(false)
                }
            }
        }
    }

    fun refreshData() {
        _refreshTrigger.value++
    }

    fun setSortCategory(category: MovieSortCategory) {
        if (_sortCategory.value != category) {
            _sortCategory.value = category
            _refreshTrigger.value++
        }
    }

    fun onMovieSelected(movie: ViewMovie) {
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(false)
                .catch { error ->
                }
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
                .onCompletion {
                }
                .catch { error ->
                   error.printStackTrace()
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
                            }
                        }
                    }
                }
        }
    }

    fun checkIntent() {
        viewModelScope.launch {
            dataUpdateInteractor.intentUrl()
                .onStart {
                }
                .onCompletion {
                }
                .catch { error ->
                }
                .collect { url ->
                    _urlData.emit(url)
                }
        }
    }

    fun onConfirmAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    dataUpdateInteractor.requestFile(type.force, type.hasPartUpdate)
                    checkIntent()
                }

                is AlertType.UpdateAll -> {
                    dataUpdateInteractor.updateAll()
                }

                else -> {
                }
            }
        }
    }

    fun onCancelAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    dataUpdateInteractor.resetUpdate()
                }

                else -> {
                }
            }
        }
    }

    fun onNeutralAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    _alert.trySend(
                        Alert(
                            title = ResourceString(R.string.full_update_title),
                            content = ResourceString(R.string.full_update_description),
                            btnOk = ResourceString(android.R.string.ok),
                            btnCancel = ResourceString(android.R.string.cancel),
                            type = AlertType.Update(true, type.hasPartUpdate)
                        )
                    )
                }

                else -> {
                }
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

    fun updateDownloadProgress(movieId: Long, percent: Float) {
        _downloadProgress.update { current ->
            current.toMutableMap().apply {
                if (percent >= 100f) {
                    remove(movieId)
                } else {
                    put(movieId, percent)
                }
            }
        }
    }
}
