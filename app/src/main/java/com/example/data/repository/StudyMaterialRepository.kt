package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.StudyMaterial
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

class StudyMaterialRepository(
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

    private fun materialsCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("study_materials")

    private fun requireUserId(): String {
        require(collegeId.isNotBlank()) { "A college must be unlocked and selected first." }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing study materials.")
    }

    fun observeMyMaterials(): Flow<List<StudyMaterial>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/study_materials"
        emitAll(
            materialsCollection()
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(StudyMaterial::class.java) }
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

    fun observeSharedMaterials(): Flow<List<StudyMaterial>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/study_materials"
        emitAll(
            materialsCollection()
                .whereEqualTo("isShared", true)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(StudyMaterial::class.java) }
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

    suspend fun addMaterial(material: StudyMaterial, chunks: List<String> = emptyList()): String {
        val uid = requireUserId()
        val docRef = materialsCollection().document()
        val hasChunks = chunks.isNotEmpty()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "userName" to (material.userName.ifBlank { auth.currentUser?.displayName ?: "Student" }),
            "title" to material.title.trim(),
            "subject" to material.subject.trim(),
            "courseCode" to material.courseCode.trim().uppercase(),
            "semester" to material.semester.trim(),
            "professor" to material.professor.trim(),
            "type" to material.type,
            "tags" to material.tags,
            "content" to material.content,
            "driveUrl" to material.driveUrl.trim(),
            "fileName" to material.fileName.trim(),
            "localPath" to material.localPath.trim(),
            "fileSize" to material.fileSize,
            "mimeType" to material.mimeType.trim(),
            "fileBase64" to material.fileBase64,
            "totalChunks" to if (hasChunks) chunks.size else material.totalChunks,
            "hasChunks" to (hasChunks || material.hasChunks),
            "isShared" to material.isShared,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()

            // Save chunked payload for files up to 3MB strictly inside this college's material doc
            if (hasChunks) {
                try {
                    val batch = db.batch()
                    chunks.forEachIndexed { idx, chunkData ->
                        val chunkDoc = docRef.collection("file_chunks").document("chunk_$idx")
                        batch.set(chunkDoc, mapOf("chunkIndex" to idx, "data" to chunkData))
                    }
                    batch.commit().await()
                } catch (batchErr: Exception) {
                    chunks.forEachIndexed { idx, chunkData ->
                        val chunkDoc = docRef.collection("file_chunks").document("chunk_$idx")
                        chunkDoc.set(mapOf("chunkIndex" to idx, "data" to chunkData)).await()
                    }
                }
            }

            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMaterial(material: StudyMaterial) {
        val uid = requireUserId()
        require(material.id.isNotBlank()) { "Material ID required for update" }
        requireNotNull(material.createdAt) { "CreatedAt must not be null" }

        val docRef = materialsCollection().document(material.id)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "userName" to material.userName,
            "title" to material.title.trim(),
            "subject" to material.subject.trim(),
            "courseCode" to material.courseCode.trim().uppercase(),
            "semester" to material.semester.trim(),
            "professor" to material.professor.trim(),
            "type" to material.type,
            "tags" to material.tags,
            "content" to material.content,
            "driveUrl" to material.driveUrl.trim(),
            "fileName" to material.fileName.trim(),
            "localPath" to material.localPath.trim(),
            "fileSize" to material.fileSize,
            "mimeType" to material.mimeType.trim(),
            "fileBase64" to material.fileBase64,
            "totalChunks" to material.totalChunks,
            "hasChunks" to material.hasChunks,
            "isShared" to material.isShared,
            "createdAt" to material.createdAt,
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

    suspend fun fetchFileChunks(materialId: String): List<String> {
        requireUserId()
        val path = "colleges/$collegeId/study_materials/$materialId/file_chunks"
        try {
            val snapshot = materialsCollection()
                .document(materialId)
                .collection("file_chunks")
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val chunks = snapshot.documents
                    .sortedBy { it.getLong("chunkIndex") ?: 0L }
                    .mapNotNull { it.getString("data") }
                if (chunks.isNotEmpty()) {
                    return chunks
                }
            }

            val directChunks = mutableListOf<String>()
            for (idx in 0 until 12) {
                val doc = materialsCollection()
                    .document(materialId)
                    .collection("file_chunks")
                    .document("chunk_$idx")
                    .get()
                    .await()
                val data = doc.getString("data")
                if (data != null && data.isNotBlank()) {
                    directChunks.add(data)
                } else {
                    break
                }
            }
            return directChunks
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            return emptyList()
        }
    }

    suspend fun deleteMaterial(id: String) {
        requireUserId()
        val docRef = materialsCollection().document(id)
        try {
            try {
                val chunks = docRef.collection("file_chunks").get().await()
                if (!chunks.isEmpty) {
                    val batch = db.batch()
                    chunks.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            } catch (ignored: Exception) {
                // best effort subcollection cleanup
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
