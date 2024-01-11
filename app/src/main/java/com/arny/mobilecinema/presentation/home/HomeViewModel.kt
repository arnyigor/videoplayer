package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.extendedsearch.ExtendSearchResult
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel @AssistedInject constructor(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {
    companion object {
        val SEARCH_TYPES = listOf(
            AppConstants.SearchType.CINEMA,
            AppConstants.SearchType.SERIAL,
        )
    }

    private var kpRange: SimpleFloatRange = SimpleFloatRange()
    private var imdbRange: SimpleFloatRange = SimpleFloatRange()
    private var years: SimpleIntRange = SimpleIntRange()
    private var countries: List<String> = emptyList()
    private var queryString = ""
    private var mlikesPriority = true
    private var searchType = ""
    private var searchAddTypes = SEARCH_TYPES
    private var genres: List<String> = emptyList()
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _empty = MutableStateFlow(false)
    val empty = _empty.asStateFlow()
    private val _emptyExtended = MutableStateFlow(false)
    val emptyExtended = _emptyExtended.asStateFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()
    private val _alert = BufferedChannel<Alert>()
    val alert = _alert.receiveAsFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _order = MutableStateFlow("")
    val order = _order.asStateFlow()
    private var search = UiAction.Search()
    private val trigger = MutableSharedFlow<Unit>()
    private val actionStateFlow = MutableSharedFlow<UiAction>()
    var moviesDataFlow: Flow<PagingData<ViewMovie>> =
        listOf(
            trigger,
            actionStateFlow.filterIsInstance<UiAction.Search>()
                .distinctUntilChanged()
                .debounce(350)
        )
            .merge()
            .onStart {
                val savedOrder = moviesInteractor.getOrder()
                _order.value = savedOrder
                emit(
                    UiAction.Search(
                        order = savedOrder,
                        searchType = searchType,
                        searchAddTypes = searchAddTypes
                    )
                )
            }
            .flatMapLatest { trig ->
                if (trig is UiAction.Search) {
                    this.search = trig
                }
                val dataFlow = moviesInteractor.getMovies(
                    search = search.query,
                    order = search.order,
                    searchType = search.searchType,
                    searchAddTypes = search.searchAddTypes,
                    genres = search.genres,
                    countries = search.countries,
                    years = search.years,
                    imdbs = search.imdbs,
                    kps = search.kps,
                    likesPriority = mlikesPriority
                )
                dataFlow
            }
            .onEach {
                _loading.value = false
                checkEmpty()
            }
            .cachedIn(viewModelScope)

    private fun checkEmpty() {
        viewModelScope.launch {
            moviesInteractor.isMoviesEmpty()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> _empty.value = data.result
                    }
                }
        }
    }

    fun downloadData(force: Boolean) {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(force)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                            result.throwable.printStackTrace()
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
                                        btnNeutral = if (hasPartUpdate) ResourceString(R.string.full_update) else null,
                                        type = AlertType.Update(false, hasPartUpdate)
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun loadMovies(
        query: String = "",
        submit: Boolean = true,
        delay: Boolean = false,
        resetAll: Boolean = false
    ) {
        viewModelScope.launch {
            queryString = query
            if (submit) {
                if (delay) {
                    delay(350)
                }
                when {
                    resetAll -> {
                        resetExtendSearch()
                        actionStateFlow.emit(
                            UiAction.Search(
                                order = _order.value,
                                searchAddTypes = searchAddTypes
                            )
                        )
                    }

                    query.isNotBlank() -> {
                        actionStateFlow.emit(
                            UiAction.Search(
                                searchType = searchType,
                                query = query,
                                order = _order.value,
                                searchAddTypes = query.takeIf { it.isNotBlank() }
                                    ?.let { searchAddTypes } ?: emptyList()
                            )
                        )
                    }

                    else -> {
                        trigger.emit(Unit)
                    }
                }
            }
        }
    }

    fun onConfirmAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    _toast.emit(ResourceString(R.string.update_started))
                    dataUpdateInteractor.requestFile(type.force, type.hasPartUpdate)
                }
                else -> {}
            }
        }
    }

    fun onCancelAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    dataUpdateInteractor.resetUpdate()
                }

                else -> {}
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

                else -> {}
            }
        }
    }

    fun setOrder(order: String, likesPriority: Boolean) {
        viewModelScope.launch {
            moviesInteractor.saveOrder(order)
            _order.value = order
            mlikesPriority = likesPriority
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = queryString,
                    order = _order.value,
                    searchAddTypes = searchAddTypes,
                    genres = genres,
                    countries = countries,
                    years = years,
                    imdbs = imdbRange,
                    kps = kpRange,
                    likesPriority = mlikesPriority
                )
            )
        }
    }

    fun setSearchType(type: String, submit: Boolean = true, addTypes: List<String>) {
        viewModelScope.launch {
            searchType = type
            searchAddTypes = addTypes
            if (submit) {
                trigger.emit(Unit)
            }
        }
    }

    fun extendedSearch(searchResult: ExtendSearchResult) {
        viewModelScope.launch {
            searchType = AppConstants.SearchType.TITLE
            searchAddTypes = searchResult.types
            queryString = searchResult.search
            genres = searchResult.genres
            countries = searchResult.countries
            years = searchResult.yearsRange
            imdbRange = searchResult.imdbRange
            kpRange = searchResult.kpRange
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = queryString,
                    order = _order.value,
                    searchAddTypes = searchAddTypes,
                    genres = genres,
                    countries = countries,
                    years = years,
                    imdbs = imdbRange,
                    kps = kpRange,
                    likesPriority = mlikesPriority
                )
            )
        }
    }

    private fun resetExtendSearch() {
        searchType = AppConstants.SearchType.TITLE
        searchAddTypes = SEARCH_TYPES
        queryString = ""
        genres = emptyList()
        countries = emptyList()
        years = SimpleIntRange()
        imdbRange = SimpleFloatRange()
        kpRange = SimpleFloatRange()
    }
}