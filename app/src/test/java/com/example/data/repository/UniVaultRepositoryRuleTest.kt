package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.model.ScheduleItem
import com.example.data.model.StudyMaterial
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class UniVaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueCode(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        return "${prefix}_$suffix"
    }

    @Test
    fun collegeGate_createAndIsolateMaterialsPerCollege() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val collegeRepo = CollegeRepository(firestore)

        val codeA = uniqueCode("MIT")
        val codeB = uniqueCode("STAN")

        val collegeA = withTimeout(DEFAULT_TIMEOUT_MS) {
            collegeRepo.createCollege("MIT Engineering", codeA, "CS Dept")
        }
        val collegeB = withTimeout(DEFAULT_TIMEOUT_MS) {
            collegeRepo.createCollege("Stanford AI", codeB, "AI Lab")
        }

        val matRepoA = StudyMaterialRepository(firestore, collegeA.collegeId)
        val matRepoB = StudyMaterialRepository(firestore, collegeB.collegeId)

        val matId = withTimeout(DEFAULT_TIMEOUT_MS) {
            matRepoA.addMaterial(
                StudyMaterial(
                    title = "MIT Graph Theory",
                    subject = "Computer Science",
                    courseCode = "CS201",
                    semester = "Semester 3",
                    professor = "Dr. Euler",
                    type = "LECTURE_NOTE",
                    content = "BFS and DFS proofs",
                    isShared = true,
                    tags = listOf("graphs")
                )
            )
        }
        assertTrue(matId.isNotBlank())

        val materialsInA = withTimeout(FLOW_TIMEOUT_MS) {
            matRepoA.observeMyMaterials().first { it.any { m -> m.id == matId } }
        }
        assertTrue(materialsInA.any { it.title == "MIT Graph Theory" && it.collegeId == collegeA.collegeId })

        val materialsInB = withTimeout(FLOW_TIMEOUT_MS) {
            matRepoB.observeMyMaterials().first()
        }
        assertFalse(materialsInB.any { it.id == matId })
    }

    @Test
    fun collegeGate_peerCannotAccessLockedCollegeUntilValidCodeEntered() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceCollegeRepo = CollegeRepository(firestore)
        val secretCode = uniqueCode("GATE")
        val college = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCollegeRepo.createCollege("Harvard Law", secretCode)
        }

        val aliceMatRepo = StudyMaterialRepository(firestore, college.collegeId)
        val sharedMatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMatRepo.addMaterial(
                StudyMaterial(
                    title = "Constitutional Law Outline",
                    subject = "Law",
                    courseCode = "LAW101",
                    type = "LECTURE_NOTE",
                    isShared = true
                )
            )
        }

        // Bob signs in without entering the special code
        signInTestUser(BOB_EMAIL)
        val bobCollegeRepo = CollegeRepository(firestore)
        val bobMatRepo = StudyMaterialRepository(firestore, college.collegeId)

        // Reading materials before unlocking MUST fail with PERMISSION_DENIED
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobMatRepo.observeSharedMaterials().first()
            }
            fail("Expected PERMISSION_DENIED before unlocking college")
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException()
            assertNotNull(root)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, root?.code)
        }

        // Entering an invalid code fails cleanly
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobCollegeRepo.unlockCollegeByCode("INVALID_CODE_999")
            }
            fail("Expected error for invalid code")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid special code") == true)
        }

        // Bob enters the valid special code
        val unlocked = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCollegeRepo.unlockCollegeByCode(secretCode.lowercase())
        }
        assertEquals(college.collegeId, unlocked.collegeId)
        assertEquals("Harvard Law", unlocked.collegeName)

        // Now Bob can see the shared materials inside Harvard Law
        val sharedList = withTimeout(FLOW_TIMEOUT_MS) {
            bobMatRepo.observeSharedMaterials().first { it.any { m -> m.id == sharedMatId } }
        }
        assertTrue(sharedList.any { it.title == "Constitutional Law Outline" })
    }

    @Test
    fun schedule_createAndObserveInsideCollege_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val collegeRepo = CollegeRepository(firestore)
        val college = withTimeout(DEFAULT_TIMEOUT_MS) {
            collegeRepo.createCollege("Berkeley EECS", uniqueCode("CAL"))
        }
        val repository = ScheduleRepository(firestore, college.collegeId)

        val item = ScheduleItem(
            title = "CS201 Midterm Exam",
            courseCode = "CS201",
            dayOfWeek = "Thursday",
            timeSlot = "10:00 - 12:00",
            type = "EXAM",
            dueDate = "2026-11-20",
            syllabusNotes = "Chapters 1 to 5"
        )

        val id = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addSchedule(item)
        }
        assertNotNull(id)

        val schedules = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSchedules().first { it.any { s -> s.id == id } }
        }
        assertTrue(schedules.any { it.courseCode == "CS201" && it.collegeId == college.collegeId })
    }

    @Test
    fun studyMaterial_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = StudyMaterialRepository(firestore, "col_unauth")

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMyMaterials().first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException()
            assertTrue(
                e is IllegalStateException ||
                    (root != null && root.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
        }
    }

    @Test
    fun flashcardDeck_createObserveUpdateAndIsolate_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val collegeRepo = CollegeRepository(firestore)
        val secretCode = uniqueCode("FC")
        val college = withTimeout(DEFAULT_TIMEOUT_MS) {
            collegeRepo.createCollege("MIT Flashcards Lab", secretCode)
        }
        val aliceRepo = FlashcardRepository(firestore, college.collegeId)

        val cards = listOf(
            FlashcardItem(
                id = "card_1",
                front = "What is Dijkstra's algorithm time complexity with binary heap?",
                back = "O((V + E) log V)",
                hint = "Priority queue extract-min",
                topic = "Shortest Paths"
            )
        )
        val deck = FlashcardDeck(
            title = "Graph Algorithms Deck",
            subject = "Algorithms",
            courseCode = "CS202",
            sourceNoteId = "note_1",
            sourceNoteTitle = "Shortest Paths Notes",
            sourceExcerpt = "Dijkstra uses a min-priority queue giving O((V+E) log V).",
            cardsCount = cards.size,
            masteredCount = 0,
            cardsJson = FlashcardItem.serializeList(cards)
        )

        val deckId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createDeck(deck)
        }
        assertTrue(deckId.isNotBlank())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeDecks().first { list -> list.any { it.id == deckId } }
        }
        val saved = observed.first { it.id == deckId }
        assertEquals("Graph Algorithms Deck", saved.title)
        assertEquals(0, saved.masteredCount)

        // Update mastery progress
        val updatedCards = cards.map { it.copy(isMastered = true, reviewCount = 1) }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updateDeck(
                saved.copy(
                    masteredCount = 1,
                    cardsJson = FlashcardItem.serializeList(updatedCards)
                )
            )
        }
        val afterUpdate = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeDecks().first { list -> list.any { it.id == deckId && it.masteredCount == 1 } }
        }
        assertEquals(1, afterUpdate.first { it.id == deckId }.masteredCount)

        // Bob (unlocked member) cannot see Alice's personal flashcard deck
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            CollegeRepository(firestore).unlockCollegeByCode(secretCode)
        }
        val bobDecks = withTimeout(FLOW_TIMEOUT_MS) {
            FlashcardRepository(firestore, college.collegeId).observeDecks().first()
        }
        assertFalse(bobDecks.any { it.id == deckId })
    }

    @Test
    fun flashcardDeck_unauthenticatedUser_failsCleanly() = runBlocking {
        auth.signOut()
        val repo = FlashcardRepository(firestore, "col_unauth")
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeDecks().first()
            }
            fail("Expected exception for unauthenticated flashcard deck observer")
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException()
            assertTrue(
                e is IllegalStateException ||
                    (root != null && root.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@univault.edu"
        const val BOB_EMAIL = "bob@univault.edu"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
