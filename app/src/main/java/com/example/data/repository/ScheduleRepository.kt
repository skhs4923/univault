package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ScheduleItem
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ScheduleRepository(
    private val db: FirebaseFirestore,
    private val collegeId: String
) {

    constructor(context: Context, collegeId: String) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        collegeId
    )

    private val auth = Firebase.auth

    private fun schedulesCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("schedules")

    private fun requireUserId(): String {
        require(collegeId.isNotBlank()) { "A college must be unlocked and selected first." }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google to access timetable.")
    }

    fun observeSchedules(): Flow<List<ScheduleItem>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/schedules"
        emitAll(
            schedulesCollection()
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ScheduleItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addSchedule(item: ScheduleItem): String {
        val uid = requireUserId()
        val docRef = schedulesCollection().document()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "title" to item.title.trim(),
            "courseCode" to item.courseCode.trim().uppercase(),
            "dayOfWeek" to item.dayOfWeek,
            "timeSlot" to item.timeSlot,
            "type" to item.type,
            "dueDate" to item.dueDate,
            "isCompleted" to item.isCompleted,
            "syllabusNotes" to item.syllabusNotes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleCompleted(item: ScheduleItem) {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val docRef = schedulesCollection().document(item.id)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "title" to item.title,
            "courseCode" to item.courseCode,
            "dayOfWeek" to item.dayOfWeek,
            "timeSlot" to item.timeSlot,
            "type" to item.type,
            "dueDate" to item.dueDate,
            "isCompleted" to !item.isCompleted,
            "syllabusNotes" to item.syllabusNotes,
            "createdAt" to item.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSchedule(id: String) {
        requireUserId()
        val docRef = schedulesCollection().document(id)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
