package com.arny.mobilecinema.presentation.extendedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.presentation.uimodels.Dialog
import com.arny.mobilecinema.presentation.uimodels.DialogType
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.SimpleString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExtendedSearchViewModel @Inject constructor(
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {
    companion object {
        val R_TYPES_RES = listOf(
            R.string.types_films,
            R.string.types_serials,
        )
        const val DIALOG_REQ_TYPES = 1000
        const val DIALOG_REQ_GENRES = 1001
        const val DIALOG_REQ_COUNTRIES = 1002
    }

    private var genres = listOf<SelectUIModel>()
    private var countries = listOf<SelectUIModel>()
    private var selectedTypes = listOf<String>()
    private var search = ""
    private var yearFrom = 0
    private var yearTo = 0
    private var imdbFrom = 0.0f
    private var imdbTo = 0.0f
    private var kpFrom = 0.0f
    private var kpTo = 0.0f
    private val _types = MutableStateFlow(emptyList<IWrappedString>())
    val types = _types.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _showContent = MutableStateFlow(false)
    val showContent = _showContent.asStateFlow()
    private val _selectedGenres = MutableStateFlow(emptyList<SelectUIModel>())
    val selectedGenres = _selectedGenres.asStateFlow()
    private val _yearsRange = MutableStateFlow<SimpleIntRange?>(null)
    val yearsRange = _yearsRange.asStateFlow()
    private val _selectedCountries = MutableStateFlow(emptyList<SelectUIModel>())
    val selectedCountries = _selectedCountries.asStateFlow()
    private val _dialog = BufferedChannel<Dialog>()
    val dialog = _dialog.receiveAsFlow()
    private val _onResult = BufferedChannel<ExtendSearchResult>()
    val onResult = _onResult.receiveAsFlow()

    init {
        viewModelScope.launch {
            genres = moviesInteractor.loadDistinctGenres().mapIndexed { index, s ->
                SelectUIModel(
                    id = index,
                    title = s,
                    selected = false
                )
            }
            countries = moviesInteractor.getCountries().mapIndexed { index, s ->
                SelectUIModel(
                    id = index,
                    title = s,
                    selected = false
                )
            }
            _yearsRange.value = moviesInteractor.getMinMaxYears()
            _loading.value = false
            _showContent.value = true
        }
    }

    fun onGenreSelectChanged(indices: IntArray) {
        viewModelScope.launch {
            genres = genres.toMutableList().apply {
                for (index in indices) {
                    this[index] = this[index].copy(selected = true)
                }
            }
            _selectedGenres.value = genres.filter { it.selected }.toList()
        }
    }

    fun onCountriesSelectChanged(indices: IntArray) {
        viewModelScope.launch {
            countries = countries.toMutableList().apply {
                for (index in indices) {
                    this[index] = this[index].copy(selected = true)
                }
            }
            _selectedCountries.value = countries.filter { it.selected }.toList()
        }
    }

    fun onTypesClicked() {
        viewModelScope.launch {
            _dialog.trySend(
                Dialog(
                    request = DIALOG_REQ_TYPES,
                    title = ResourceString(R.string.choose_type),
                    content = null,
                    btnOk = ResourceString(android.R.string.ok),
                    btnCancel = ResourceString(android.R.string.cancel),
                    cancelable = true,
                    type = DialogType.MultiChoose(
                        R_TYPES_RES.map { ResourceString(it) }
                    )
                )
            )
        }
    }

    fun onTypesChosen(indices: IntArray) {
        viewModelScope.launch {
            _types.value = R_TYPES_RES.filterIndexed { index, _ -> index in indices }
                .map {
                    ResourceString(it)
                }
            selectedTypes = selectedTypes.toMutableList().apply {
                clear()
                addAll(
                    R_TYPES_RES.filterIndexed { index, _ -> index in indices }.map {
                        if (it == R.string.types_films) {
                            AppConstants.SearchType.CINEMA
                        } else {
                            AppConstants.SearchType.SERIAL
                        }
                    }
                )
            }.toList()
        }
    }

    fun onGenresClicked() {
        viewModelScope.launch {
            _dialog.trySend(
                Dialog(
                    request = DIALOG_REQ_GENRES,
                    title = ResourceString(R.string.choose_genre),
                    content = null,
                    btnOk = ResourceString(android.R.string.ok),
                    btnCancel = ResourceString(android.R.string.cancel),
                    cancelable = true,
                    type = DialogType.MultiChoose(
                        genres.map { SimpleString(it.title) },
                        genres.mapIndexedNotNull { index, model ->
                            if (model.selected) {
                                index
                            } else {
                                null
                            }
                        }
                    )
                )
            )
        }
    }

    fun onCountriesClicked() {
        viewModelScope.launch {
            _dialog.trySend(
                Dialog(
                    request = DIALOG_REQ_COUNTRIES,
                    title = ResourceString(R.string.choose_countries),
                    content = null,
                    btnOk = ResourceString(android.R.string.ok),
                    btnCancel = ResourceString(android.R.string.cancel),
                    cancelable = true,
                    type = DialogType.MultiChoose(
                        countries.map { SimpleString(it.title) },
                        countries.mapIndexedNotNull { index, model ->
                            if (model.selected) {
                                index
                            } else {
                                null
                            }
                        }
                    )
                )
            )
        }
    }

    fun onRemoveTypesClicked() {
        _types.value = emptyList()
        selectedTypes = emptyList()
    }

    fun onRemoveGenresClicked() {
        _selectedGenres.value = emptyList()
        genres = genres.map {
            it.copy(selected = false)
        }
    }

    fun onRemoveCountriesClicked() {
        _selectedCountries.value = emptyList()
        countries = countries.map {
            it.copy(selected = false)
        }
    }

    fun updateYears(from: Float?, to: Float?) {
        if (from != null && to != null) {
            yearFrom = from.toInt()
            yearTo = to.toInt()
        }
    }

    fun updateImdb(from: Float?, to: Float?) {
        if (from != null && to != null) {
            imdbFrom = from
            imdbTo = to
        }
    }

    fun updateKp(from: Float?, to: Float?) {
        if (from != null && to != null) {
            kpFrom = from
            kpTo = to
        }
    }

    fun onResultClick() {
        _onResult.trySend(
            ExtendSearchResult(
                search = this.search,
                types = selectedTypes,
                genres = _selectedGenres.value.filter { it.selected }.map { it.title },
                countries = _selectedCountries.value.filter { it.selected }.map { it.title },
                yearsRange = SimpleIntRange(yearFrom, yearTo),
                imdbRange = SimpleFloatRange(imdbFrom, imdbTo),
            )
        )
    }

    fun onSearchChange(search: String) {
        this.search = search
    }
}
