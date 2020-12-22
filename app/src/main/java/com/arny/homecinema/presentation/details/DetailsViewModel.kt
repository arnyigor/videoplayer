package com.arny.homecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.SerialEpisode
import com.arny.homecinema.presentation.utils.SingleLiveEvent
import com.arny.homecinema.presentation.utils.mutableLiveData
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
    val episodes = SingleLiveEvent<DataResult<List<SerialEpisode>>>()
    val episode = SingleLiveEvent<DataResult<SerialEpisode?>>()
    fun loadVideo(movie: Movie) {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            if (data.value == null) {
                loading.value = true
                videoRepository.loadMovie(movie, true)
                    .onCompletion { loading.value = false }
                    .catch { data.value = DataResult.Error(it) }
                    .collect { res ->
                        data.value = res
                    }
            }
        }
    }

    fun onSeasonChange(position: Int) {
        viewModelScope.launch {
            videoRepository.onSeasonChanged(position)
                .onCompletion { loading.value = false }
                .catch { data.value = DataResult.Error(it) }
                .collect { res ->
                    episodes.value = res
                }
        }
    }

    fun onEpisodeChange(position: Int) {
        viewModelScope.launch {
            videoRepository.onEpisodeChanged(position)
                .onCompletion { loading.value = false }
                .catch { data.value = DataResult.Error(it) }
                .collect { res ->
                    episode.value = res
                }
        }
    }
}
