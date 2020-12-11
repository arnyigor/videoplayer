package com.arny.videoplayer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.presentation.models.VideoItem
import com.arny.videoplayer.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel  @Inject constructor(
    private val videoRepository: VideoRepository,
) : ViewModel() {
    val loading = mutableLiveData(false)
    val result = mutableLiveData<DataResult<List<VideoItem>>>()

    init {
        restartLoading()
    }

    fun restartLoading() {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.getAllVideos()
                .map { list -> list.map { VideoItem(it) } }
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = DataResult.Error(it)
                }
                .collect {
                    result.value = DataResult.Success(it)
                }
        }
    }
}