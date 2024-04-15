package com.arny.mobilecinema.data.firebase

import com.arny.mobilecinema.domain.interactors.feedback.FeedbackDatabase
import com.google.firebase.database.FirebaseDatabase
import javax.inject.Inject

class FeedbackDatabaseImpl @Inject constructor(
    val database: FirebaseDatabase
) : FeedbackDatabase {
    override suspend fun sendMessage(pageUrl: String, content: String): Boolean {
        database.getReference(pageUrl).setValue(content)
        return true
    }
}