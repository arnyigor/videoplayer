package com.arny.homecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.toResult
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.VideoSearchLink
import com.arny.homecinema.presentation.utils.mutableLiveData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
) : ViewModel() {
    val loading = mutableLiveData(false)
    val result = mutableLiveData<DataResult<MainPageContent>>()

    init {
        restartLoading()
    }

    fun restartLoading() {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.getAllVideos()
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = DataResult.Error(it)
                }
                .collect {
                    result.value = it
                }
        }
    }

    fun search(search: String) {
        if (search.isBlank()) {
            restartLoading()
        } else {
            searchVideo(search)
        }
    }

    private fun searchVideo(search: String) {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.searchVideo(search)
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = DataResult.Error(it)
                }
                .collect {
                    result.value = MainPageContent(it).toResult()
                }
        }
    }

    fun onSearchChanged(searchLink: VideoSearchLink?) {
        viewModelScope.launch {
            if (loading.value == true) return@launch
            loading.value = true
            videoRepository.getAllVideos(searchLink?.searchUrl)
                .onCompletion {
                    loading.value = false
                }
                .catch {
                    result.value = DataResult.Error(it)
                }
                .collect {
                    result.value = it
                }
        }
    }
}