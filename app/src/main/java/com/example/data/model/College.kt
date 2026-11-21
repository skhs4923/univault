package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class College(
    @DocumentId val id: String = "",
    val collegeId: String = "",
    val collegeName: String = "",
    val description: String = "",
    val accessCode: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UnlockedCollege(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val collegeName: String = "",
    val description: String = "",
    val accessCode: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    @ServerTimestamp val unlockedAt: Timestamp? = null
)
