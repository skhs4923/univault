package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val title: String = "",
    val courseCode: String = "",
    val dayOfWeek: String = "Monday",
    val timeSlot: String = "09:00 - 10:30 AM",
    val type: String = "CLASS", // CLASS, EXAM, MILESTONE
    val dueDate: String = "",
    val isCompleted: Boolean = false,
    val syllabusNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
