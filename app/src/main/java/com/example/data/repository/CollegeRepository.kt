package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UnlockedCollege
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

class CollegeRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing colleges.")
    }

    fun observeUnlockedColleges(): Flow<List<UnlockedCollege>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/unlocked_colleges"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("unlocked_colleges")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UnlockedCollege::class.java)
                        .sortedByDescending { it.unlockedAt?.toDate()?.time ?: 0L }
                }
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

    suspend fun unlockCollegeByCode(rawCode: String): UnlockedCollege {
        val uid = requireUserId()
        val code = normalizeAccessCode(rawCode)
        require(isValidCodeFormat(code)) {
            "Special code must be 4-32 characters (letters, numbers, '-' or '_')."
        }

        val codeDocRef = db.collection("college_codes").document(code)
        val codeSnap = try {
            codeDocRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, codeDocRef.path)
            throw e
        }

        if (!codeSnap.exists()) {
            throw IllegalArgumentException("Invalid special code \"$code\". No matching college found.")
        }

        val collegeId = codeSnap.getString("collegeId").orEmpty()
        val collegeName = codeSnap.getString("collegeName").orEmpty()
        val description = codeSnap.getString("description").orEmpty()
        val ownerId = codeSnap.getString("ownerId").orEmpty()
        val ownerName = codeSnap.getString("ownerName").orEmpty()

        if (collegeId.isBlank() || collegeName.isBlank() || ownerId.isBlank()) {
            throw IllegalStateException("College code record is incomplete.")
        }

        val unlockedRef = db.collection("users")
            .document(uid)
            .collection("unlocked_colleges")
            .document(collegeId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "collegeName" to collegeName,
            "description" to description,
            "accessCode" to code,
            "ownerId" to ownerId,
            "ownerName" to ownerName,
            "unlockedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            unlockedRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, unlockedRef.path)
            throw e
        }

        return UnlockedCollege(
            id = collegeId,
            userId = uid,
            collegeId = collegeId,
            collegeName = collegeName,
            description = description,
            accessCode = code,
            ownerId = ownerId,
            ownerName = ownerName
        )
    }

    suspend fun createCollege(
        collegeName: String,
        rawCode: String,
        description: String = ""
    ): UnlockedCollege {
        val uid = requireUserId()
        val trimmedName = collegeName.trim()
        val trimmedDesc = description.trim()
        val code = normalizeAccessCode(rawCode)

        require(trimmedName.length in 2..120) {
            "College name must be between 2 and 120 characters."
        }
        require(isValidCodeFormat(code)) {
            "Special code must be 4-32 characters (letters, numbers, '-' or '_')."
        }

        val codeRef = db.collection("college_codes").document(code)
        val existingCode = try {
            codeRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, codeRef.path)
            throw e
        }

        if (existingCode.exists()) {
            throw IllegalStateException("Special code \"$code\" is already in use. Enter it to unlock that college, or choose another code.")
        }

        val ownerName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
        val collegeRef = db.collection("colleges").document()
        val collegeId = collegeRef.id

        val collegePayload = mutableMapOf<String, Any?>(
            "collegeId" to collegeId,
            "collegeName" to trimmedName,
            "description" to trimmedDesc,
            "accessCode" to code,
            "ownerId" to uid,
            "ownerName" to ownerName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val codePayload = mutableMapOf<String, Any?>(
            "collegeId" to collegeId,
            "collegeName" to trimmedName,
            "description" to trimmedDesc,
            "accessCode" to code,
            "ownerId" to uid,
            "ownerName" to ownerName,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val unlockedRef = db.collection("users")
            .document(uid)
            .collection("unlocked_colleges")
            .document(collegeId)

        val unlockedPayload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "collegeName" to trimmedName,
            "description" to trimmedDesc,
            "accessCode" to code,
            "ownerId" to uid,
            "ownerName" to ownerName,
            "unlockedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            collegeRef.set(collegePayload).await()
            codeRef.set(codePayload).await()
            unlockedRef.set(unlockedPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, collegeRef.path)
            throw e
        }

        return UnlockedCollege(
            id = collegeId,
            userId = uid,
            collegeId = collegeId,
            collegeName = trimmedName,
            description = trimmedDesc,
            accessCode = code,
            ownerId = uid,
            ownerName = ownerName
        )
    }

    suspend fun lockCollegeForUser(collegeId: String) {
        val uid = requireUserId()
        val ref = db.collection("users")
            .document(uid)
            .collection("unlocked_colleges")
            .document(collegeId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    companion object {
        private val CODE_REGEX = Regex("^[A-Z0-9_\\-]{4,32}$")

        fun normalizeAccessCode(raw: String): String {
            return raw.trim().uppercase().replace("\\s+".toRegex(), "")
        }

        fun isValidCodeFormat(normalizedCode: String): Boolean {
            return CODE_REGEX.matches(normalizedCode)
        }
    }
}
