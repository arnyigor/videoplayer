package com.arny.mobilecinema.domain.interactors.feedback

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.Movie
import kotlinx.coroutines.flow.Flow

interface FeedbackInteractor {
    fun sendMessage(content: String, movie: Movie?, seasonPosition: Int, episodePosition: Int): Flow<DataResult<Boolean>>
}