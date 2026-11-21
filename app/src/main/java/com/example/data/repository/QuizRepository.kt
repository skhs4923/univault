package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Quiz
import com.example.data.model.TestResult
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

class QuizRepository(
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

    private fun quizzesCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("quizzes")

    private fun testResultsCollection() =
        db.collection("colleges")
            .document(collegeId)
            .collection("test_results")

    private fun requireUserId(): String {
        require(collegeId.isNotBlank()) { "A college must be unlocked and selected first." }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google to access quizzes.")
    }

    fun observeQuizzes(): Flow<List<Quiz>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/quizzes"
        emitAll(
            quizzesCollection()
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Quiz::class.java) }
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

    suspend fun createQuiz(quiz: Quiz): String {
        val uid = requireUserId()
        val docRef = quizzesCollection().document()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "title" to quiz.title.trim(),
            "subject" to quiz.subject.trim(),
            "courseCode" to quiz.courseCode.trim().uppercase(),
            "positiveMarks" to quiz.positiveMarks,
            "negativeMarks" to quiz.negativeMarks,
            "durationMinutes" to quiz.durationMinutes,
            "questionsCount" to quiz.questionsCount,
            "questionsJson" to quiz.questionsJson,
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

    fun observeTestResults(): Flow<List<TestResult>> = flow {
        val uid = requireUserId()
        val path = "colleges/$collegeId/test_results"
        emitAll(
            testResultsCollection()
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TestResult::class.java) }
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

    suspend fun saveTestResult(result: TestResult): String {
        val uid = requireUserId()
        val docRef = testResultsCollection().document()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "collegeId" to collegeId,
            "quizId" to result.quizId,
            "quizTitle" to result.quizTitle,
            "subject" to result.subject,
            "score" to result.score,
            "totalMarks" to result.totalMarks,
            "accuracy" to result.accuracy,
            "summary" to result.summary,
            "answersJson" to result.answersJson,
            "weakTopics" to result.weakTopics,
            "createdAt" to FieldValue.serverTimestamp()
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
}
