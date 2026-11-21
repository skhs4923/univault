package com.example.util

import com.example.data.model.DiscussionMessage
import com.example.data.model.FlashcardDeck
import com.example.data.model.Quiz
import com.example.data.model.ScheduleItem
import com.example.data.model.StudyMaterial
import com.example.data.model.TestResult
import com.example.ui.viewmodel.PerformanceMetrics
import com.example.ui.viewmodel.ScheduleMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkspaceAnalysisSnapshot(
    val collegeName: String = "",
    val materials: List<StudyMaterial> = emptyList(),
    val schedules: List<ScheduleItem> = emptyList(),
    val scheduleMetrics: ScheduleMetrics = ScheduleMetrics(),
    val quizzes: List<Quiz> = emptyList(),
    val testResults: List<TestResult> = emptyList(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val flashcardDecks: List<FlashcardDeck> = emptyList(),
    val discussions: List<DiscussionMessage> = emptyList()
) {
    val classesCount: Int get() = schedules.count { it.type == "CLASS" }
    val examsCount: Int get() = schedules.count { it.type == "EXAM" }
    val milestonesCount: Int get() = schedules.count { it.type == "MILESTONE" }
    val hasAnyData: Boolean
        get() = materials.isNotEmpty() ||
            schedules.isNotEmpty() ||
            quizzes.isNotEmpty() ||
            testResults.isNotEmpty() ||
            flashcardDecks.isNotEmpty() ||
            discussions.isNotEmpty()
}

enum class WorkspaceFocusMode(val label: String, val description: String) {
    FULL_AUDIT(
        "Full 360° Study Audit",
        "Analyze Vault files, Calendar deadlines, Quiz scores, Progress analytics, Flashcards, and Campus Discussions together."
    ),
    EXAM_READINESS(
        "Exam & Deadline Plan",
        "Cross-reference upcoming exams and syllabus milestones with your Vault notes and weak topics."
    ),
    WEAKNESS_COACH(
        "Progress & Quiz Coach",
        "Pinpoint low-accuracy subjects, test mistakes, and flashcard mastery gaps with targeted practice steps."
    ),
    NOTES_AND_DISCUSS(
        "Files & Discussion Digest",
        "Synthesize key concepts from your Vault materials and peer questions in Discuss channels."
    )
}

object WorkspaceContextBuilder {

    fun buildContextPrompt(
        snapshot: WorkspaceAnalysisSnapshot,
        includeVault: Boolean = true,
        includeCalendar: Boolean = true,
        includeProgress: Boolean = true,
        includeDiscuss: Boolean = true,
        includeQuizzes: Boolean = true
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("=== UNIVAULT STUDENT WORKSPACE SNAPSHOT ===")
        if (snapshot.collegeName.isNotBlank()) {
            sb.appendLine("College Workspace: ${snapshot.collegeName}")
        }
        sb.appendLine("Reference Date: $today")
        sb.appendLine()

        if (includeVault) {
            sb.appendLine("## 1. VAULT DATA & FILES (${snapshot.materials.size} items)")
            if (snapshot.materials.isEmpty()) {
                sb.appendLine("- No study materials or files uploaded yet.")
            } else {
                val byType = snapshot.materials.groupingBy { it.type }.eachCount()
                sb.appendLine(
                    "- Breakdown: ${byType["LECTURE_NOTE"] ?: 0} Lecture Notes, " +
                        "${byType["QUESTION_PAPER"] ?: 0} Question Papers, " +
                        "${byType["QUESTION_BANK"] ?: 0} Question Banks"
                )
                snapshot.materials.take(25).forEachIndexed { idx, mat ->
                    val fileInfo = buildString {
                        if (mat.fileName.isNotBlank()) append(" | File: ${mat.fileName}")
                        if (mat.mimeType.isNotBlank()) append(" (${mat.mimeType})")
                        if (mat.driveUrl.isNotBlank()) append(" | Drive link attached")
                    }
                    val tagsPart = if (mat.tags.isNotEmpty()) " | Tags: ${mat.tags.joinToString(", ")}" else ""
                    val profPart = if (mat.professor.isNotBlank()) " | Prof: ${mat.professor}" else ""
                    sb.appendLine("  [${idx + 1}] ${mat.title} (${mat.subject}, ${mat.courseCode}, ${mat.semester}, ${mat.type}$profPart$fileInfo$tagsPart)")
                    if (mat.content.isNotBlank()) {
                        val excerpt = mat.content.replace(Regex("\\s+"), " ").trim().take(280)
                        sb.appendLine("      Note Excerpt: $excerpt")
                    }
                }
            }
            sb.appendLine()
        }

        if (includeCalendar) {
            val classes = snapshot.schedules.filter { it.type == "CLASS" }
            val exams = snapshot.schedules.filter { it.type == "EXAM" }
            val milestones = snapshot.schedules.filter { it.type == "MILESTONE" }
            sb.appendLine("## 2. CALENDAR (Classes: ${classes.size}, Exams: ${exams.size}, Milestones: ${snapshot.scheduleMetrics.completedMilestones}/${snapshot.scheduleMetrics.totalMilestones} done)")
            if (snapshot.schedules.isEmpty()) {
                sb.appendLine("- No timetable classes, exams, or syllabus milestones added yet.")
            } else {
                if (exams.isNotEmpty()) {
                    sb.appendLine("- Exams:")
                    exams.take(15).forEach { exam ->
                        val status = if (exam.isCompleted) "Completed" else "Upcoming"
                        val dateStr = exam.dueDate.ifBlank { exam.dayOfWeek }
                        val notes = if (exam.syllabusNotes.isNotBlank()) " | Notes: ${exam.syllabusNotes.take(120)}" else ""
                        sb.appendLine("  * [$status] ${exam.title} (${exam.courseCode}) on $dateStr at ${exam.timeSlot}$notes")
                    }
                }
                if (milestones.isNotEmpty()) {
                    sb.appendLine("- Syllabus Milestones:")
                    milestones.take(15).forEach { ms ->
                        val status = if (ms.isCompleted) "Done" else "Pending"
                        val dateStr = ms.dueDate.ifBlank { ms.dayOfWeek }
                        val notes = if (ms.syllabusNotes.isNotBlank()) " | Details: ${ms.syllabusNotes.take(120)}" else ""
                        sb.appendLine("  * [$status] ${ms.title} (${ms.courseCode}) due $dateStr (${ms.timeSlot})$notes")
                    }
                }
                if (classes.isNotEmpty()) {
                    sb.appendLine("- Weekly Timetable Classes (${classes.size}):")
                    classes.take(20).forEach { cls ->
                        val notes = if (cls.syllabusNotes.isNotBlank()) " | Room/Info: ${cls.syllabusNotes.take(80)}" else ""
                        sb.appendLine("  * ${cls.dayOfWeek} ${cls.timeSlot}: ${cls.title} (${cls.courseCode})$notes")
                    }
                }
            }
            sb.appendLine()
        }

        if (includeProgress) {
            val perf = snapshot.performanceMetrics
            val totalFlashcards = snapshot.flashcardDecks.sumOf { it.cardsCount }
            val masteredFlashcards = snapshot.flashcardDecks.sumOf { it.masteredCount }
            sb.appendLine("## 3. ACADEMIC PROGRESS & MASTERY")
            sb.appendLine("- Syllabus Milestone Completion: ${snapshot.scheduleMetrics.completedMilestones}/${snapshot.scheduleMetrics.totalMilestones} (${snapshot.scheduleMetrics.completionPercentage.toInt()}%)")
            sb.appendLine("- Practice Tests Taken: ${perf.testsTaken} | Average Accuracy: ${perf.averageScorePct.toInt()}% | Highest Accuracy: ${perf.highestScorePct.toInt()}%")
            if (perf.subjectPerformance.isNotEmpty()) {
                val breakdown = perf.subjectPerformance.entries.joinToString(", ") { "${it.key}: ${it.value.toInt()}%" }
                sb.appendLine("- Subject Accuracy Breakdown: $breakdown")
            }
            if (perf.weakTopicList.isNotEmpty()) {
                sb.appendLine("- Identified Weak Topics (from quiz mistakes): ${perf.weakTopicList.joinToString(", ")}")
            }
            if (snapshot.testResults.isNotEmpty()) {
                sb.appendLine("- Recent Test Attempts:")
                snapshot.testResults.take(10).forEach { res ->
                    val weak = if (res.weakTopics.isNotBlank()) " | Weak topics: ${res.weakTopics}" else ""
                    sb.appendLine("  * ${res.quizTitle} (${res.subject}): ${res.score.toInt()}/${res.totalMarks.toInt()} (${res.accuracy.toInt()}%)$weak")
                }
            }
            if (snapshot.flashcardDecks.isNotEmpty()) {
                sb.appendLine("- Flashcard Decks (${snapshot.flashcardDecks.size} decks, $masteredFlashcards/$totalFlashcards cards mastered):")
                snapshot.flashcardDecks.take(10).forEach { deck ->
                    val src = if (deck.sourceNoteTitle.isNotBlank()) " [from note: ${deck.sourceNoteTitle}]" else ""
                    sb.appendLine("  * ${deck.title} (${deck.subject}, ${deck.courseCode}): ${deck.masteredCount}/${deck.cardsCount} mastered$src")
                }
            }
            sb.appendLine()
        }

        if (includeQuizzes) {
            sb.appendLine("## 4. AVAILABLE QUIZZES & PRACTICE TESTS (${snapshot.quizzes.size} quizzes)")
            if (snapshot.quizzes.isEmpty()) {
                sb.appendLine("- No custom or AI-generated quizzes saved yet.")
            } else {
                snapshot.quizzes.take(15).forEach { q ->
                    sb.appendLine("  * ${q.title} (${q.subject}, ${q.courseCode}) • ${q.questionsCount} Qs • +${q.positiveMarks}/-${q.negativeMarks} marks • ${q.durationMinutes}m")
                }
            }
            sb.appendLine()
        }

        if (includeDiscuss) {
            sb.appendLine("## 5. CAMPUS DISCUSSIONS (${snapshot.discussions.size} recent messages)")
            if (snapshot.discussions.isEmpty()) {
                sb.appendLine("- No campus discussion messages posted yet.")
            } else {
                val channels = snapshot.discussions.map { it.channel }.distinct()
                sb.appendLine("- Active Channels: ${channels.joinToString(", ")}")
                snapshot.discussions.take(30).forEach { msg ->
                    val author = msg.userName.ifBlank { "Student" }
                    val cleanText = msg.text.replace(Regex("\\s+"), " ").trim().take(200)
                    sb.appendLine("  * [#${msg.channel}] $author: $cleanText")
                }
            }
            sb.appendLine()
        }

        return sb.toString().trim()
    }
}
