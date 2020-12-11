package com.arny.videoplayer.presentation.details

import androidx.lifecycle.ViewModel
import com.arny.videoplayer.data.repository.VideoRepository
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

}