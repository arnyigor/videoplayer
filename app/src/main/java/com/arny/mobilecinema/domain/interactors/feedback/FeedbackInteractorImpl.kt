package com.arny.mobilecinema.domain.interactors.feedback

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class FeedbackInteractorImpl @Inject constructor(
    private val feedbackDatabase: FeedbackDatabase
) : FeedbackInteractor {
    override fun sendMessage(
        content: String,
        movie: Movie?,
        seasonPosition: Int,
        episodePosition: Int
    ): Flow<DataResult<Boolean>> = doAsync {
        val feedback = StringBuilder().apply {
            append("PageUrl:").append(movie?.pageUrl).append("\n")
            append("Title:").append(movie?.title).append("\n")
            append("Type:").append(movie?.type).append("\n")
            if (movie?.type == MovieType.SERIAL) {
                append("Serial season:").append(seasonPosition)
                append(" episode:").append(episodePosition).append("\n")
            }
            append("Comment:").append(content).append("\n")
        }.toString()
        val reference = "${movie?.pageUrl}/${UUID.randomUUID()}"
        feedbackDatabase.sendMessage(reference, feedback)
    }
}