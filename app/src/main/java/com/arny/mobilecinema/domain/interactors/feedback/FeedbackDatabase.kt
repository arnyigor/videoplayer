package com.arny.mobilecinema.domain.interactors.feedback

interface FeedbackDatabase {
    suspend fun sendMessage(pageUrl: String, content: String): Boolean
}