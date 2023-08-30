package com.arny.mobilecinema.presentation.extendedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExtendedSearchViewModel @Inject constructor(
    moviesInteractor: MoviesInteractor,
) : ViewModel() {
    private val _genres = MutableStateFlow(emptyList<GenreUIModel>())
    val genres = _genres.asStateFlow()

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
}
