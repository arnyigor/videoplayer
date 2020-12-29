package com.arny.homecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.data.utils.getFullError
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
) : ViewModel() {
    private val loading = mutableLiveData(false)
    val result = mutableLiveData<DataResult<List<Movie>>>()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.getAllCached()
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = getFullError(it)
                }
                .collect {
                    result.value = it
                }
        }
    }

    fun searchCached(searchText: String) {
        viewModelScope.launch {
            videoRepository.searchCached(searchText)
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = getFullError(it)
                }
                .collect {
                    result.value = it.toResult()
                }
        }
    }
}