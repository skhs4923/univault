package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FlashcardDeck
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

class FlashcardRepository(
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

    private fun decksCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("flashcard_decks")

    private fun requireUserId(): String {
        require(collegeId.isNotBlank()) { "A college must be unlocked and selected first." }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google to access flashcards.")
    }

    fun observeDecks(): Flow<List<FlashcardDeck>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/flashcard_decks"
        emitAll(
            decksCollection()
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FlashcardDeck::class.java) }
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

    suspend fun createDeck(deck: FlashcardDeck): String {
        val uid = requireUserId()
        val docRef = decksCollection().document()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "title" to deck.title.trim().take(200),
            "subject" to deck.subject.trim().ifBlank { "General Study" }.take(100),
            "courseCode" to deck.courseCode.trim().uppercase().take(50),
            "sourceNoteId" to deck.sourceNoteId.trim().take(128),
            "sourceNoteTitle" to deck.sourceNoteTitle.trim().take(200),
            "sourceExcerpt" to deck.sourceExcerpt.trim().take(20000),
            "cardsCount" to deck.cardsCount,
            "masteredCount" to deck.masteredCount,
            "cardsJson" to deck.cardsJson.take(200000),
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

    suspend fun updateDeck(deck: FlashcardDeck) {
        requireUserId()
        require(deck.id.isNotBlank()) { "Deck ID must not be blank for update" }
        val docRef = decksCollection().document(deck.id)
        val updates = mapOf(
            "title" to deck.title.trim().take(200),
            "subject" to deck.subject.trim().ifBlank { "General Study" }.take(100),
            "courseCode" to deck.courseCode.trim().uppercase().take(50),
            "sourceNoteId" to deck.sourceNoteId.trim().take(128),
            "sourceNoteTitle" to deck.sourceNoteTitle.trim().take(200),
            "sourceExcerpt" to deck.sourceExcerpt.trim().take(20000),
            "cardsCount" to deck.cardsCount,
            "masteredCount" to deck.masteredCount,
            "cardsJson" to deck.cardsJson.take(200000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDeck(deckId: String) {
        requireUserId()
        require(deckId.isNotBlank()) { "Deck ID must not be blank for delete" }
        val docRef = decksCollection().document(deckId)
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
