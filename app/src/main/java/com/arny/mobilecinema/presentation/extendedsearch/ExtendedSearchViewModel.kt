package com.arny.mobilecinema.presentation.extendedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.presentation.uimodels.Dialog
import com.arny.mobilecinema.presentation.uimodels.DialogType
import com.arny.mobilecinema.presentation.utils.BufferedSharedFlow
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExtendedSearchViewModel @Inject constructor(
    moviesInteractor: MoviesInteractor,
) : ViewModel() {
    companion object {
        val R_TYPES = listOf(
            R.string.types_films,
            R.string.types_serials,
        )
    }

    private val _genres = MutableStateFlow(emptyList<GenreUIModel>())
    val genres = _genres.asStateFlow()
    private val _types = MutableStateFlow(emptyList<IWrappedString>())
    val types = _types.asStateFlow()
    private val _dialog = BufferedSharedFlow<Dialog>()
    val dialog = _dialog.asSharedFlow()

    init {
        viewModelScope.launch {
            _genres.value = moviesInteractor.loadDistinctGenres().mapIndexed { index, s ->
                GenreUIModel(
                    id = index,
                    title = s,
                    selected = false
                )
            }
        }
    }

    fun onGenreSelectChanged(position: Int, checked: Boolean) {
        _genres.value = _genres.value.toMutableList().apply {
            getOrNull(position)?.let {
                set(
                    position, it.copy(
                        selected = checked
                    )
                )
            }
        }
    }

    fun onTypesClicked() {
        viewModelScope.launch {
            _dialog.emit(
                Dialog(
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
}
