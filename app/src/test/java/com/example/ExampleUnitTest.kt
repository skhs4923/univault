package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DiscussionMessage
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.model.Quiz
import com.example.data.model.ScheduleItem
import com.example.data.model.StudyMaterial
import com.example.data.model.TestResult
import com.example.data.repository.AiTutorRepository
import com.example.notifications.ClassReminderNotificationHelper
import com.example.notifications.ClassReminderScheduler
import com.example.ui.viewmodel.ChatMessage
import com.example.ui.viewmodel.FlashcardViewModel
import com.example.ui.viewmodel.PerformanceMetrics
import com.example.ui.viewmodel.ScheduleMetrics
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.util.DocumentStorageUtil
import com.example.util.TimetableScheduleHelper
import com.example.util.VaultMaterialAnalyzer
import com.example.util.WorkspaceAnalysisSnapshot
import com.example.util.WorkspaceContextBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testMimeTypeResolution() {
        assertEquals("application/pdf", DocumentStorageUtil.resolveMimeType("lecture_notes.pdf", null))
        assertEquals("application/vnd.ms-powerpoint", DocumentStorageUtil.resolveMimeType("slides.ppt", null))
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", DocumentStorageUtil.resolveMimeType("slides.pptx", null))
        assertEquals("image/png", DocumentStorageUtil.resolveMimeType("diagram.png", null))
        assertEquals("video/mp4", DocumentStorageUtil.resolveMimeType("recording.mp4", null))
    }

    @Test
    fun testStudyMaterialDefaultShared() {
        val material = StudyMaterial(
            title = "Calculus Notes",
            fileName = "calc.pdf",
            fileBase64 = "JVBERi0xLjQK",
            totalChunks = 4,
            hasChunks = true
        )
        assertTrue(material.isShared)
        assertEquals("calc.pdf", material.fileName)
        assertEquals("JVBERi0xLjQK", material.fileBase64)
        assertEquals(4, material.totalChunks)
        assertTrue(material.hasChunks)
    }

    @Test
    fun testMaxCloudSyncSizeIsAtLeast3MB() {
        assertTrue(DocumentStorageUtil.MAX_CLOUD_SYNC_SIZE_BYTES >= 3 * 1024 * 1024L)
    }

    @Test
    fun testParseTimetableJsonAndNormalization() {
        val repo = AiTutorRepository()
        val sampleJson = """
            {
              "classes": [
                {
                  "title": "Operating Systems",
                  "courseCode": "cs301",
                  "dayOfWeek": "Mon",
                  "timeSlot": "09:00 - 10:00 AM",
                  "notes": "Room B-204"
                },
                {
                  "title": "Database Management Systems",
                  "courseCode": "",
                  "dayOfWeek": "thu",
                  "timeSlot": "02:00 - 03:30 PM",
                  "notes": ""
                },
                {
                  "title": "   ",
                  "courseCode": "FREE",
                  "dayOfWeek": "Friday",
                  "timeSlot": "12:00 - 01:00 PM"
                }
              ]
            }
        """.trimIndent()

        val parsed = repo.parseTimetableJson(sampleJson)
        assertEquals(2, parsed.size)

        assertEquals("Operating Systems", parsed[0].title)
        assertEquals("CS301", parsed[0].courseCode)
        assertEquals("Monday", parsed[0].dayOfWeek)
        assertEquals("09:00 - 10:00 AM", parsed[0].timeSlot)
        assertEquals("Room B-204", parsed[0].notes)

        assertEquals("Database Management Systems", parsed[1].title)
        assertEquals("DMS", parsed[1].courseCode)
        assertEquals("Thursday", parsed[1].dayOfWeek)
        assertEquals("02:00 - 03:30 PM", parsed[1].timeSlot)
        assertEquals("", parsed[1].notes)
    }

    @Test
    fun testDecodeScaledTimetableBitmapSucceeds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File.createTempFile("timetable_test_", ".png", context.cacheDir)
        try {
            val sourceBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            FileOutputStream(tempFile).use { out ->
                sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            sourceBitmap.recycle()

            val decoded = ScheduleViewModel.decodeScaledTimetableBitmap(context, Uri.fromFile(tempFile))
            assertTrue(decoded.width in 1..1600)
            assertTrue(decoded.height in 1..1600)
            decoded.recycle()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIsRetryableModelErrorDetectsHighDemand() {
        val repo = AiTutorRepository()
        assertTrue(repo.isRetryableModelError(IllegalStateException("This model is currently in high demand. Please try again later.")))
        assertTrue(repo.isRetryableModelError(IllegalStateException("HTTP 503 Service Unavailable")))
        assertEquals(false, repo.isRetryableModelError(IllegalArgumentException("Unsupported MIME type")))
    }

    @Test
    fun testTimetableTimeParsingAndUpcomingArrangement() {
        assertEquals(540 to 600, TimetableScheduleHelper.parseTimeRangeMinutes("09:00 - 10:00 AM"))
        assertEquals(660 to 750, TimetableScheduleHelper.parseTimeRangeMinutes("11:00 - 12:30 PM"))
        assertEquals(840 to 930, TimetableScheduleHelper.parseTimeRangeMinutes("02:00 - 03:30 PM"))
        assertEquals(840 to 930, TimetableScheduleHelper.parseTimeRangeMinutes("14:00 - 15:30"))

        // Fix simulated "now" at Wednesday 10:30 AM
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val wedPast = ScheduleItem(id = "1", title = "Early Wed", courseCode = "CS101", dayOfWeek = "Wednesday", timeSlot = "08:00 - 09:00 AM")
        val wedLive = ScheduleItem(id = "2", title = "Live Wed", courseCode = "CS102", dayOfWeek = "Wednesday", timeSlot = "10:00 - 11:30 AM")
        val thuClass = ScheduleItem(id = "3", title = "Thu Class", courseCode = "CS103", dayOfWeek = "Thursday", timeSlot = "09:00 - 10:00 AM")
        val friClass = ScheduleItem(id = "4", title = "Fri Class", courseCode = "CS104", dayOfWeek = "Friday", timeSlot = "02:00 - 03:00 PM")

        val upcoming = TimetableScheduleHelper.arrangeClasses(
            classes = listOf(friClass, wedPast, thuClass, wedLive),
            showAll = false,
            nowMillis = nowCal.timeInMillis
        )
        assertEquals(listOf("2", "3"), upcoming.map { it.item.id })
        assertTrue(upcoming[0].isOngoing)

        val all = TimetableScheduleHelper.arrangeClasses(
            classes = listOf(friClass, wedPast, thuClass, wedLive),
            showAll = true,
            nowMillis = nowCal.timeInMillis
        )
        // Live Wed -> Thu -> Fri -> next week's Wed early class
        assertEquals(listOf("2", "3", "4", "1"), all.map { it.item.id })
    }

    @Test
    fun testComputeNextReminderTriggerMillis15MinutesBeforeClass() {
        // Wednesday Nov 18, 2026 at 09:30 AM
        val wed930 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 9, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedWed945 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 9, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerToday = TimetableScheduleHelper.computeNextReminderTriggerMillis(
            dayOfWeek = "Wednesday",
            timeSlot = "10:00 - 11:00 AM",
            leadMinutes = 15,
            nowMillis = wed930.timeInMillis
        )
        assertEquals(expectedWed945.timeInMillis, triggerToday)

        // At 09:50 AM (past 09:45 reminder window), schedules next Wednesday Nov 25 at 09:45 AM
        val wed950 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 9, 50, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedNextWed945 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 9, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerNextWeek = TimetableScheduleHelper.computeNextReminderTriggerMillis(
            dayOfWeek = "Wednesday",
            timeSlot = "10:00 - 11:00 AM",
            leadMinutes = 15,
            nowMillis = wed950.timeInMillis
        )
        assertEquals(expectedNextWed945.timeInMillis, triggerNextWeek)
    }

    @Test
    fun testClassReminderSchedulerSchedulesAndCancelsAlarms() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(notificationManager).setNotificationsEnabled(true)

        ClassReminderNotificationHelper.setRemindersEnabled(app, true)

        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedWed945 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 9, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val items = listOf(
            ScheduleItem(
                id = "cls_os",
                title = "Operating Systems",
                courseCode = "CS301",
                dayOfWeek = "Wednesday",
                timeSlot = "10:00 - 11:00 AM",
                type = "CLASS",
                syllabusNotes = "Room B-204"
            ),
            ScheduleItem(
                id = "exam_mid",
                title = "Midterm Exam",
                courseCode = "CS301",
                dayOfWeek = "Friday",
                timeSlot = "02:00 - 05:00 PM",
                type = "EXAM"
            )
        )

        val scheduledCount = ClassReminderScheduler.syncAndScheduleForCollege(
            context = app,
            collegeId = "college_test",
            collegeName = "Test College",
            classes = items,
            nowMillis = nowCal.timeInMillis
        )
        assertEquals(1, scheduledCount)
        assertEquals(2, ClassReminderNotificationHelper.getAllReminders(app).size)

        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
        assertEquals(expectedWed945.timeInMillis, shadowAlarm.scheduledAlarms[0].triggerAtMs)

        // Pausing reminders cancels scheduled alarms
        ClassReminderNotificationHelper.setRemindersEnabled(app, false)
        val pausedCount = ClassReminderScheduler.rescheduleAllStoredReminders(app, nowCal.timeInMillis)
        assertEquals(0, pausedCount)
        assertEquals(0, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun testArrangeDatedItemsChronologicalOrderingAndUpcomingFilter() {
        // Now: Wednesday Nov 18, 2026 at 10:30 AM
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pastExam = ScheduleItem(id = "e1", title = "Past Quiz", courseCode = "CS101", type = "EXAM", dueDate = "2026-11-17", timeSlot = "09:00 - 11:00 AM")
        val liveExam = ScheduleItem(id = "e2", title = "Ongoing Lab Exam", courseCode = "CS201", type = "EXAM", dueDate = "2026-11-18", timeSlot = "10:00 - 12:00 PM")
        val laterTodayExam = ScheduleItem(id = "e3", title = "Afternoon Exam", courseCode = "CS202", type = "EXAM", dueDate = "2026-11-18", timeSlot = "02:00 - 04:00 PM")
        val futureMilestone = ScheduleItem(id = "e4", title = "Final Report", courseCode = "CS301", type = "EXAM", dueDate = "2026-11-25", timeSlot = "09:00 AM")

        val upcoming = TimetableScheduleHelper.arrangeDatedItems(
            items = listOf(futureMilestone, pastExam, laterTodayExam, liveExam),
            showAll = false,
            nowMillis = nowCal.timeInMillis
        )
        assertEquals(listOf("e2", "e3", "e4"), upcoming.map { it.item.id })
        assertTrue(upcoming[0].isOngoing)
        assertTrue(upcoming[1].isNextUp)

        val all = TimetableScheduleHelper.arrangeDatedItems(
            items = listOf(futureMilestone, pastExam, laterTodayExam, liveExam),
            showAll = true,
            nowMillis = nowCal.timeInMillis
        )
        assertEquals(listOf("e2", "e3", "e4", "e1"), all.map { it.item.id })
        assertTrue(all.last().isPast)
    }

    @Test
    fun testExamAndMilestone15MinuteRemindersAndJsonParsing() {
        val repo = AiTutorRepository()
        val json = """
            {
              "items": [
                {
                  "title": "Operating Systems Final",
                  "courseCode": "cs301",
                  "dueDate": "2026-11-25",
                  "timeSlot": "02:00 - 05:00 PM",
                  "notes": "Hall A"
                }
              ]
            }
        """.trimIndent()
        val parsed = repo.parseSchedulePhotoJson(json, "EXAM")
        assertEquals(1, parsed.size)
        assertEquals("CS301", parsed[0].courseCode)
        assertEquals("2026-11-25", parsed[0].dueDate)
        assertEquals("02:00 - 05:00 PM", parsed[0].timeSlot)

        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedTrigger = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 13, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val trigger = TimetableScheduleHelper.computeDatedReminderTriggerMillis(
            dueDate = "2026-11-25",
            timeSlot = "02:00 - 05:00 PM",
            leadMinutes = 15,
            nowMillis = nowCal.timeInMillis
        )
        assertEquals(expectedTrigger.timeInMillis, trigger)
    }

    @Test
    fun testChatMessagesCreatedSimultaneouslyHaveUniqueIds() {
        val ids = (1..25).map { ChatMessage(sender = if (it % 2 == 0) "ai" else "user", text = "msg $it").id }
        assertEquals(25, ids.toSet().size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun testSanitizeJsonObjectStripsMarkdownFences() {
        val repo = AiTutorRepository()
        val wrapped = """
            ```json
            {"classes":[{"title":"Algorithms","courseCode":"CS202","dayOfWeek":"Tue","timeSlot":"10:00 - 11:00 AM"}]}
            ```
        """.trimIndent()
        val parsed = repo.parseTimetableJson(wrapped)
        assertEquals(1, parsed.size)
        assertEquals("Algorithms", parsed[0].title)
        assertEquals("Tuesday", parsed[0].dayOfWeek)
    }

    @Test
    fun testFlashcardSerializationAndAiJsonParsing() {
        val original = listOf(
            FlashcardItem(
                id = "c1",
                front = "What is Dijkstra's algorithm time complexity with a binary heap?",
                back = "O((V + E) log V)",
                hint = "Min-priority queue operations",
                topic = "Graph Algorithms",
                isMastered = true,
                reviewCount = 3
            ),
            FlashcardItem(
                id = "c2",
                front = "Does Dijkstra handle negative edge weights?",
                back = "No, use Bellman-Ford when negative edges exist.",
                hint = "Greedy assumption",
                topic = "Shortest Paths",
                isMastered = false,
                reviewCount = 1
            )
        )
        val encoded = FlashcardItem.serializeList(original)
        val decoded = FlashcardItem.parseList(encoded)
        assertEquals(2, decoded.size)
        assertEquals(original[0].front, decoded[0].front)
        assertEquals(original[0].isMastered, decoded[0].isMastered)
        assertEquals(original[1].hint, decoded[1].hint)

        val repo = AiTutorRepository()
        val aiJson = """
            ```json
            {
              "deckTitle": "OS Synchronization Flashcards",
              "cards": [
                {
                  "front": "What is a mutex?",
                  "back": "A mutual exclusion lock held by one thread at a time.",
                  "hint": "Binary ownership",
                  "topic": "Concurrency"
                },
                {
                  "front": " ",
                  "back": "Should be skipped"
                }
              ]
            }
            ```
        """.trimIndent()
        val (title, parsedCards) = repo.parseFlashcardsJson(aiJson, fallbackTitle = "Fallback", fallbackTopic = "OS")
        assertEquals("OS Synchronization Flashcards", title)
        assertEquals(1, parsedCards.size)
        assertEquals("What is a mutex?", parsedCards[0].front)
        assertEquals("Concurrency", parsedCards[0].topic)
    }

    @Test
    fun testSplitNoteSegmentsSplitsParagraphsAndLines() {
        val multiPara = "Paragraph one about BFS.\n\nParagraph two about DFS.\n\nParagraph three about cycles."
        assertEquals(3, FlashcardViewModel.splitNoteSegments(multiPara).size)

        val singleLines = "Line 1: CAP theorem\nLine 2: Paxos\nLine 3: Raft"
        assertEquals(3, FlashcardViewModel.splitNoteSegments(singleLines).size)

        assertTrue(FlashcardViewModel.splitNoteSegments("   ").isEmpty())
    }

    @Test
    fun testWorkspaceContextBuilderIncludesAllPillarsAndHonorsToggles() {
        val snapshot = WorkspaceAnalysisSnapshot(
            collegeName = "MIT Engineering",
            materials = listOf(
                StudyMaterial(
                    id = "m1",
                    title = "OS Process Scheduling Notes",
                    subject = "Operating Systems",
                    courseCode = "CS301",
                    fileName = "os_ch3.pdf",
                    content = "Round Robin scheduling gives each process a fixed time quantum."
                )
            ),
            schedules = listOf(
                ScheduleItem(id = "s1", title = "OS Lecture", courseCode = "CS301", dayOfWeek = "Monday", timeSlot = "09:00 - 10:00 AM", type = "CLASS"),
                ScheduleItem(id = "s2", title = "OS Midterm", courseCode = "CS301", dueDate = "2026-11-25", timeSlot = "02:00 - 04:00 PM", type = "EXAM"),
                ScheduleItem(id = "s3", title = "Unit 2 Lab", courseCode = "CS301", dueDate = "2026-11-20", timeSlot = "05:00 PM", type = "MILESTONE", isCompleted = true)
            ),
            scheduleMetrics = ScheduleMetrics(totalMilestones = 1, completedMilestones = 1, upcomingExams = 1, completionPercentage = 100f),
            quizzes = listOf(
                Quiz(id = "q1", title = "CPU Scheduling Quiz", subject = "Operating Systems", courseCode = "CS301", questionsCount = 5, positiveMarks = 4, negativeMarks = 1.0)
            ),
            testResults = listOf(
                TestResult(id = "r1", quizTitle = "CPU Scheduling Quiz", subject = "Operating Systems", score = 16.0, totalMarks = 20.0, accuracy = 80.0, weakTopics = "Deadlocks")
            ),
            performanceMetrics = PerformanceMetrics(
                testsTaken = 1,
                averageScorePct = 80.0,
                highestScorePct = 80.0,
                subjectPerformance = mapOf("Operating Systems" to 80.0),
                weakTopicList = listOf("Deadlocks")
            ),
            flashcardDecks = listOf(
                FlashcardDeck(id = "d1", title = "OS Terms", subject = "Operating Systems", courseCode = "CS301", cardsCount = 10, masteredCount = 6)
            ),
            discussions = listOf(
                DiscussionMessage(id = "dm1", userName = "Alex", channel = "CS301", text = "Can someone explain Banker's algorithm safety check?")
            )
        )

        val fullPrompt = WorkspaceContextBuilder.buildContextPrompt(snapshot)
        assertTrue(fullPrompt.contains("MIT Engineering"))
        assertTrue(fullPrompt.contains("OS Process Scheduling Notes"))
        assertTrue(fullPrompt.contains("os_ch3.pdf"))
        assertTrue(fullPrompt.contains("Round Robin scheduling"))
        assertTrue(fullPrompt.contains("OS Midterm"))
        assertTrue(fullPrompt.contains("80%"))
        assertTrue(fullPrompt.contains("Deadlocks"))
        assertTrue(fullPrompt.contains("6/10 mastered"))
        assertTrue(fullPrompt.contains("CPU Scheduling Quiz"))
        assertTrue(fullPrompt.contains("Banker's algorithm safety check"))

        val vaultOnly = WorkspaceContextBuilder.buildContextPrompt(
            snapshot = snapshot,
            includeVault = true,
            includeCalendar = false,
            includeProgress = false,
            includeDiscuss = false,
            includeQuizzes = false
        )
        assertTrue(vaultOnly.contains("VAULT DATA & FILES"))
        assertFalse(vaultOnly.contains("CAMPUS DISCUSSIONS"))
        assertFalse(vaultOnly.contains("AVAILABLE QUIZZES"))
    }

    @Test
    fun testVaultMaterialAnalyzerExtractsTopicsAndSynthesizesNotesBanksPapersAndFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempTextFile = File.createTempFile("vault_notes_", ".txt", context.cacheDir)
        try {
            tempTextFile.writeText("Banker's Algorithm prevents deadlocks by checking safe sequences before resource allocation.")

            val materials = listOf(
                StudyMaterial(
                    id = "m_note",
                    title = "Deadlocks Lecture Notes",
                    subject = "Operating Systems",
                    courseCode = "CS301",
                    type = "LECTURE_NOTE",
                    content = "Four Coffman conditions: mutual exclusion, hold and wait, no preemption, circular wait.",
                    tags = listOf("Deadlocks", "Banker's Algorithm")
                ),
                StudyMaterial(
                    id = "m_bank",
                    title = "CS301 Question Bank",
                    subject = "Operating Systems",
                    courseCode = "CS301",
                    type = "QUESTION_BANK",
                    content = "Q1: Derive the minimum frames needed to avoid thrashing."
                ),
                StudyMaterial(
                    id = "m_paper",
                    title = "EndSem 2025 Question Paper",
                    subject = "Operating Systems",
                    courseCode = "CS301",
                    type = "QUESTION_PAPER",
                    fileName = "os_endsem_notes.txt",
                    mimeType = "text/plain",
                    localPath = tempTextFile.absolutePath
                )
            )
            val schedules = listOf(
                ScheduleItem(id = "s1", title = "Virtual Memory Lab", courseCode = "CS301", type = "CLASS")
            )

            val topics = VaultMaterialAnalyzer.extractSelectableTopics(
                materials = materials,
                schedules = schedules,
                weakTopics = listOf("Page Replacement")
            )
            assertEquals("Page Replacement", topics.first())
            assertTrue(topics.contains("Deadlocks"))
            assertTrue(topics.contains("Banker's Algorithm"))
            assertTrue(topics.contains("CS301 Question Bank"))
            assertTrue(topics.contains("Virtual Memory Lab"))

            assertTrue(VaultMaterialAnalyzer.isPlainTextFile("notes.md", ""))
            assertTrue(VaultMaterialAnalyzer.isGeminiSupportedBinaryMime("application/pdf"))
            assertFalse(VaultMaterialAnalyzer.isGeminiSupportedBinaryMime("application/vnd.openxmlformats-officedocument.presentationml.presentation"))

            val built = kotlinx.coroutines.runBlocking {
                VaultMaterialAnalyzer.buildVaultQuizContext(
                    context = context,
                    repository = null,
                    materials = materials,
                    selectedTopic = "Deadlocks"
                )
            }
            assertEquals(3, built.analyzedCount)
            assertTrue(built.promptText.contains("Lecture Notes: 1 | Question Banks: 1 | Question Papers: 1"))
            assertTrue(built.promptText.contains("[LECTURE NOTE] Deadlocks Lecture Notes"))
            assertTrue(built.promptText.contains("[QUESTION BANK] CS301 Question Bank"))
            assertTrue(built.promptText.contains("[QUESTION PAPER] EndSem 2025 Question Paper"))
            assertTrue(built.promptText.contains("Coffman conditions"))
            assertTrue(built.promptText.contains("avoid thrashing"))
            assertTrue(built.promptText.contains("Banker's Algorithm prevents deadlocks"))
        } finally {
            tempTextFile.delete()
        }
    }

    @Test
    fun testParsePracticeQuizJsonClampsIndexAndStripsFences() {
        val repo = AiTutorRepository()
        val raw = """
            ```json
            {
              "quizTitle": "OS Deadlock Mastery",
              "questions": [
                {
                  "questionText": "Which condition is NOT required for deadlock?",
                  "options": ["Mutual Exclusion", "Hold and Wait", "Preemption", "Circular Wait"],
                  "correctIndex": 2,
                  "explanation": "No preemption is required, so forced preemption breaks deadlock.",
                  "topic": "Deadlocks"
                },
                {
                  "questionText": "Clamp out-of-range index check",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctIndex": 99,
                  "explanation": "Clamps cleanly to last valid option."
                }
              ]
            }
            ```
        """.trimIndent()

        val (title, questions) = repo.parsePracticeQuizJson(raw, fallbackTitle = "Fallback", fallbackTopic = "OS")
        assertEquals("OS Deadlock Mastery", title)
        assertEquals(2, questions.size)
        assertEquals(2, questions[0].correctIndex)
        assertEquals("Deadlocks", questions[0].topic)
        assertEquals(3, questions[1].correctIndex)
        assertEquals("OS", questions[1].topic)
    }
}
