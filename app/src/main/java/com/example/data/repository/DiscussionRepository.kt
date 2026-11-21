package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DiscussionMessage
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DiscussionRepository(
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

    private fun messagesCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("discussion_messages")

    private fun requireUserId(): String {
        require(collegeId.isNotBlank()) { "A college must be unlocked and selected first." }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google to use discussions.")
    }

    fun observeMessages(channel: String): Flow<List<DiscussionMessage>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/discussion_messages"
        emitAll(
            messagesCollection()
                .whereEqualTo("channel", channel)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DiscussionMessage::class.java) }
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

    fun observeRecentMessages(limit: Long = 50): Flow<List<DiscussionMessage>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/discussion_messages"
        emitAll(
            messagesCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DiscussionMessage::class.java) }
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

    suspend fun sendMessage(channel: String, text: String) {
        val uid = requireUserId()
        val userName = auth.currentUser?.displayName ?: "Student"
        val docRef = messagesCollection().document()
        val payload = mapOf(
            "userId" to uid,
            "collegeId" to collegeId,
            "userName" to userName,
            "channel" to channel,
            "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
