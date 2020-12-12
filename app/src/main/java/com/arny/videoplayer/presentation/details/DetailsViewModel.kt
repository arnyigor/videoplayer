package com.arny.videoplayer.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.di.models.Video
import com.arny.videoplayer.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {
    val loading = mutableLiveData(false)
    private var currentVideo: Video? = null
    val data = mutableLiveData<DataResult<Video>?>()
    fun loadVideo(video: Video) {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            if (data.value == null) {
                loading.value = true
                videoRepository.loadVideo(video)
                    .onCompletion { loading.value = false }
                    .catch { data.value = DataResult.Error(it) }
                    .collect { res ->
                        if (res is DataResult.Success) {
                            currentVideo = res.data
                        }
                        data.value = res
                    }
            }
        }
    }
}