package com.arny.videoplayer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch


class HomeViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {
    val loading = mutableLiveData(false)
    val text = mutableLiveData<String>()

    init {
        restartLoading()
    }

    fun restartLoading() {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.all()
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    it.printStackTrace()
                    text.value = "Ошибка запроса:${it.cause?.stackTraceToString()}"
                }
                .collect {
                    text.value = it
                }
        }
    }
}