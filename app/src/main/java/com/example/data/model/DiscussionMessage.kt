package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DiscussionMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val userName: String = "",
    val channel: String = "General",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
