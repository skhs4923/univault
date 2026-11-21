package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StudyMaterial(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val userName: String = "",
    val title: String = "",
    val subject: String = "",
    val courseCode: String = "",
    val semester: String = "Semester 1",
    val professor: String = "",
    val type: String = "LECTURE_NOTE", // LECTURE_NOTE, QUESTION_PAPER, QUESTION_BANK
    val tags: List<String> = emptyList(),
    val content: String = "",
    val driveUrl: String = "",
    val fileName: String = "",
    val localPath: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val fileBase64: String = "", // Shared file data synced via cloud Firestore
    val totalChunks: Int = 0,    // Total chunks for documents up to 3MB
    val hasChunks: Boolean = false,
    val isShared: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
