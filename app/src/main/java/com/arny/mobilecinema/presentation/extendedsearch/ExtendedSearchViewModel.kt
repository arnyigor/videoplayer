package com.arny.mobilecinema.presentation.extendedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
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
        val R_TYPES = listOf(
            R.string.types_films,
            R.string.types_serials,
        )
        const val DIALOG_REQ_TYPES = 1000
        const val DIALOG_REQ_GENRES = 1001
    }

    private var genres = listOf<GenreUIModel>()
    private var yearFrom = 0
    private var yearTo = 0
    private val _types = MutableStateFlow(emptyList<IWrappedString>())
    val types = _types.asStateFlow()
    private val _selectedGenres = MutableStateFlow(emptyList<GenreUIModel>())
    val selectedGenres = _selectedGenres.asStateFlow()
    private val _yearsRange = MutableStateFlow<YearsRangeUIModel?>(null)
    val yearsRange = _yearsRange.asStateFlow()
    private val _dialog = BufferedChannel<Dialog>()
    val dialog = _dialog.receiveAsFlow()

    init {
        viewModelScope.launch {
            genres = moviesInteractor.loadDistinctGenres().mapIndexed { index, s ->
                GenreUIModel(
                    id = index,
                    title = s,
                    selected = false
                )
            }
            _yearsRange.value = YearsRangeUIModel(1920, 2023, 2000)
        }
    }

    fun onGenreSelectChanged(indices: IntArray) {
        viewModelScope.launch {
            val uiModels = genres.toMutableList()
            uiModels.forEachIndexed { index, uiModel ->
                if (index in indices) {
                    uiModels[index] = uiModel.copy(
                        selected = true
                    )
                } else {
                    uiModels[index] = uiModel.copy(
                        selected = false
                    )
                }
            }
            _selectedGenres.value = uiModels.filter { it.selected }.toList()
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
                        R_TYPES.map { ResourceString(it) }
                    )
                )
            )
        }
    }

    fun onTypesChosen(indices: IntArray) {
        viewModelScope.launch {
            _types.value = R_TYPES.filterIndexed { index, _ -> index in indices }
                .map {
                    ResourceString(it)
                }
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

    fun onRemoveGenresClicked() {
        _selectedGenres.value = emptyList()
    }

    fun onRangeFromChange(rangeFrom: String) {
        yearFrom = rangeFrom.toIntOrNull() ?: 0
    }

    fun onRangeToChange(rangeTo: String) {
        yearTo = rangeTo.toIntOrNull() ?: 0
    }
}
