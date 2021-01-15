package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.VideoRepository
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.presentation.utils.SingleLiveEvent
import com.arny.mobilecinema.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val loading = mutableLiveData(false)
    val data = SingleLiveEvent<DataResult<Movie?>>()
    val cached = SingleLiveEvent<DataResult<Boolean>>()
    private var isRemovedFromCache = false

    fun loadVideo(movie: Movie) {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            if (data.value == null) {
                loading.value = true
                videoRepository.loadMovie(movie)
                    .onCompletion { loading.value = false }
                    .catch { data.value = getFullError(it) }
                    .collect { res ->
                        data.value = res
                    }
            }
        }
    }

    fun cacheMovie(movie: Movie?) {
        viewModelScope.launch {
            if (!isRemovedFromCache) {
                videoRepository.cacheMovie(movie)
                    .onCompletion { loading.value = false }
                    .catch { cached.value = getFullError(it) }
                    .collect { res ->
                        cached.value = res
                    }
            }
        }
    }

    fun clearCache(movie: Movie?) {
        viewModelScope.launch {
            videoRepository.clearCache(movie)
                .onCompletion { loading.value = false }
                .catch { cached.value = getFullError(it) }
                .collect {
                    isRemovedFromCache = true
                }
        }
    }
}
