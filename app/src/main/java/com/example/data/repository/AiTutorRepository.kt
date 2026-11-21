package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.FlashcardItem
import com.example.data.model.QuizQuestion
import com.example.util.VaultInlineAttachment
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "AiTutorRepository"

data class ExtractedTimetableClass(
    val title: String,
    val courseCode: String,
    val dayOfWeek: String,
    val timeSlot: String,
    val notes: String = ""
)

data class ExtractedScheduleEntry(
    val title: String,
    val courseCode: String,
    val dayOfWeek: String = "Monday",
    val dueDate: String = "",
    val timeSlot: String,
    val notes: String = ""
)

class AiTutorRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content {
                text(
                    "You are UniVault Academic AI Tutor, a clear, accurate university teaching assistant. " +
                        "Give structured, step-by-step explanations with formulas, examples, and exam tips."
                )
            }
        )
    }

    private val fallbackChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text(
                    "You are UniVault Academic AI Tutor, a clear, accurate university teaching assistant. " +
                        "Give structured, step-by-step explanations with formulas, examples, and exam tips."
                )
            }
        )
    }

    private val quizGenerationSchema = Schema.obj(
        properties = mapOf(
            "quizTitle" to Schema.string(description = "Title of generated quiz"),
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionText" to Schema.string(description = "The question text"),
                        "options" to Schema.array(
                            items = Schema.string(),
                            description = "Exactly 4 multiple choice options"
                        ),
                        "correctIndex" to Schema.integer(description = "0-based index of correct option (0, 1, 2, or 3)"),
                        "explanation" to Schema.string(description = "Detailed concept explanation for the correct answer"),
                        "topic" to Schema.string(description = "Specific syllabus topic")
                    )
                ),
                description = "List of quiz questions"
            )
        )
    )

    private val structuredQuizModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizGenerationSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            }
        )
    }

    private val fallbackQuizModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizGenerationSchema
            }
        )
    }

    private val flashcardGenerationSchema = Schema.obj(
        properties = mapOf(
            "deckTitle" to Schema.string(description = "Concise title summarizing the flashcard deck"),
            "cards" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "front" to Schema.string(description = "Clear prompt, question, term, or concept on the front of the card"),
                        "back" to Schema.string(description = "Accurate, concise explanation, definition, or formula on the back"),
                        "hint" to Schema.string(description = "Optional short memory cue or keyword"),
                        "topic" to Schema.string(description = "Subtopic or concept tag from the notes")
                    ),
                    optionalProperties = listOf("hint", "topic")
                ),
                description = "Generated active-recall flashcards derived from the selected study notes"
            )
        )
    )

    private val structuredFlashcardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashcardGenerationSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            }
        )
    }

    private val fallbackFlashcardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashcardGenerationSchema
            }
        )
    }

    private val timetableSchema = Schema.obj(
        properties = mapOf(
            "classes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Course or subject title"),
                        "courseCode" to Schema.string(description = "Course code like CS201, or a short uppercase abbreviation if not shown"),
                        "dayOfWeek" to Schema.string(description = "Full weekday name: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, or Sunday"),
                        "timeSlot" to Schema.string(description = "Time range like 09:00 - 10:00 AM"),
                        "notes" to Schema.string(description = "Room number, lab/lecture note, or instructor if shown")
                    ),
                    optionalProperties = listOf("notes")
                ),
                description = "All scheduled class sessions detected in the timetable photo"
            )
        )
    )

    private val structuredTimetableModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = timetableSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            }
        )
    }

    private val fallbackTimetableModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = timetableSchema
            }
        )
    }

    private val schedulePhotoSchema = Schema.obj(
        properties = mapOf(
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Course, exam, or syllabus milestone title"),
                        "courseCode" to Schema.string(description = "Course code like CS201, or short uppercase abbreviation if not shown"),
                        "dayOfWeek" to Schema.string(description = "Full weekday name Monday..Sunday if applicable"),
                        "dueDate" to Schema.string(description = "Exact date in YYYY-MM-DD format if shown (for exams or milestones), else empty string"),
                        "timeSlot" to Schema.string(description = "Time or range like 09:00 - 10:00 AM"),
                        "notes" to Schema.string(description = "Room, hall, instructor, or syllabus topics if shown")
                    ),
                    optionalProperties = listOf("dayOfWeek", "dueDate", "notes")
                ),
                description = "All scheduled entries detected in the photo"
            )
        )
    )

    private val structuredScheduleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schedulePhotoSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            }
        )
    }

    private val fallbackScheduleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schedulePhotoSchema
            }
        )
    }

    private fun buildChatContents(
        prompt: String,
        history: List<Pair<String, String>>,
        workspaceContext: String = ""
    ): List<com.google.firebase.ai.type.Content> {
        val contents = mutableListOf<com.google.firebase.ai.type.Content>()
        history.takeLast(10).forEach { (sender, text) ->
            if (text.isNotBlank()) {
                val role = if (sender == "user") "user" else "model"
                contents.add(content(role = role) { text(text) })
            }
        }
        val finalPrompt = if (workspaceContext.isNotBlank()) {
            """
                Use the student's live UniVault workspace context below whenever relevant to personalize your answer, reference their actual files/notes, upcoming exams/classes/milestones, quiz scores/weak topics, flashcards, or peer discussions. If their question is purely conceptual and outside their stored items, still answer accurately and connect it to any relevant courses in their workspace.

                $workspaceContext

                Student Question: $prompt
            """.trimIndent()
        } else {
            prompt
        }
        contents.add(content(role = "user") { text(finalPrompt) })
        return contents
    }

    fun streamChatResponse(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        workspaceContext: String = ""
    ): Flow<String> = flow {
        val turnContents = buildChatContents(prompt, history, workspaceContext)
        val accumulated = StringBuilder()

        suspend fun collectStream(model: GenerativeModel) {
            withTimeout(55_000L) {
                model.generateContentStream(turnContents).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI Tutor returned an empty response.")
            }
        }

        try {
            collectStream(chatModel)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException && accumulated.isBlank()) {
                Log.w(TAG, "Primary chat model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                collectStream(fallbackChatModel)
            } else if (primaryError is TimeoutCancellationException) {
                emit(accumulated.toString() + "\n\n[Response truncated after 55s timeout.]")
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            if (accumulated.isBlank() && isRetryableModelError(primaryError)) {
                Log.w(TAG, "Primary chat model busy/unavailable; falling back to gemini-3.5-flash-lite", primaryError)
                delay(1500L)
                collectStream(fallbackChatModel)
            } else {
                Log.e(TAG, "Chat stream failed", primaryError)
                throw primaryError
            }
        }
    }

    private suspend fun generateTextWithFallback(prompt: String): String {
        val promptContent = content { text(prompt) }
        return try {
            scanRawJsonWithModel(chatModel, promptContent, timeoutMs = 55_000L)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException) {
                Log.w(TAG, "Primary text model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackChatModel, promptContent)
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            if (isRetryableModelError(primaryError)) {
                Log.w(TAG, "Primary text model busy/unavailable; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackChatModel, promptContent)
            } else {
                Log.e(TAG, "AI text generation failed", primaryError)
                throw primaryError
            }
        }
    }

    suspend fun analyzeWorkspaceData(
        workspaceContext: String,
        focusModeLabel: String,
        focusInstruction: String,
        customGoal: String = ""
    ): String {
        val extraGoal = if (customGoal.isNotBlank()) {
            "\nStudent's Specific Analysis Request: ${customGoal.trim()}"
        } else {
            ""
        }
        val prompt = """
            You are an expert university academic strategist and AI Tutor analyzing a student's complete live UniVault workspace.
            Analysis Mode: $focusModeLabel
            Objective: $focusInstruction$extraGoal

            IMPORTANT RULES:
            - Ground every insight strictly in the workspace data provided below (cite specific note titles, file names, course codes, exam dates, quiz scores, weak topics, flashcard decks, or discussion channels when present).
            - Explicitly point out any empty or missing sections (e.g., if no quizzes taken yet or no notes for an upcoming exam) and recommend concrete next actions inside UniVault.
            - Structure the report cleanly with these clear sections:
              1. Executive Academic Snapshot
              2. Vault Files & Study Materials Insights
              3. Calendar, Timetable & Upcoming Exam Priorities
              4. Progress Analytics, Quizzes & Flashcards Mastery
              5. Campus Discussions Digest & Peer Doubts
              6. Prioritized Action Plan (Next 7 Days)

            $workspaceContext
        """.trimIndent()

        return generateTextWithFallback(prompt)
    }

    suspend fun reviewAssignment(title: String, course: String, assignmentContent: String): String {
        val prompt = """
            You are an expert university academic professor and evaluator.
            Please review the following student assignment submission thoroughly:
            
            Course / Subject: $course
            Assignment Title / Question: $title
            
            Student Submission:
            \"\"\"
            $assignmentContent
            \"\"\"
            
            Please provide a structured, constructive evaluation with:
            1. Executive Score & Grade Estimate (e.g., A, B+, 88/100)
            2. Strengths & Mastery of Concepts
            3. Weaknesses, Inaccuracies & Missing Key Elements
            4. Step-by-Step Suggestions for Academic Improvement
            5. Deep Explanation of Key Underlying Concepts
        """.trimIndent()

        return generateTextWithFallback(prompt)
    }

    suspend fun explainMistake(
        subject: String,
        question: String,
        studentAnswer: String,
        correctAnswer: String,
        givenExplanation: String
    ): String {
        val prompt = """
            You are a patient university tutor helping a student learn from their exam mistake.
            
            Subject: $subject
            Question: $question
            Student's Selected Answer: $studentAnswer
            Correct Answer: $correctAnswer
            Base Explanation: $givenExplanation
            
            Please provide:
            1. Why the student's answer is a common misconception and where the reasoning breaks down.
            2. The rigorous mathematical, theoretical, or logical principle that leads directly to the correct answer.
            3. A memorable rule-of-thumb or mnemonic to never repeat this mistake on the final exam.
            4. A quick follow-up practice check to solidify the concept.
        """.trimIndent()

        return generateTextWithFallback(prompt)
    }

    suspend fun generatePracticeQuiz(
        subject: String,
        courseCode: String,
        topic: String,
        questionCount: Int = 5,
        difficulty: String = "Exam-Level",
        sourceContextPrompt: String = "",
        inlineAttachments: List<VaultInlineAttachment> = emptyList()
    ): Pair<String, List<QuizQuestion>> {
        val safeSubject = subject.trim().ifBlank { "Academic Studies" }
        val safeTopic = topic.trim().ifBlank { "Comprehensive Syllabus Coverage" }
        val clampedCount = questionCount.coerceIn(3, 20)

        val materialsBlock = if (sourceContextPrompt.isNotBlank()) {
            """
            Analyze ALL provided Vault study materials below — including Lecture Notes, Question Banks, Question Papers, and any attached PDF/image documents — and derive questions closely aligned with them and the requested topic:
            $sourceContextPrompt
            """.trimIndent()
        } else {
            "No specific Vault files were attached; craft rigorous university-level questions matching the subject and topic."
        }

        val prompt = """
            Generate an academic practice quiz of exactly $clampedCount multiple choice questions for university students.
            Subject: $safeSubject
            Course Code: ${courseCode.trim().ifBlank { "GEN" }}
            Selected Topic / Syllabus Focus: $safeTopic
            Difficulty Level: $difficulty

            $materialsBlock

            Guidelines:
            - Prioritize concepts, formulas, definitions, and exam-style problem patterns found in the provided Question Papers, Question Banks, Lecture Notes, and uploaded files.
            - Ensure every question has exactly 4 realistic options, 1 unambiguous correct answer index (0-3), a helpful conceptual explanation, and a clear topic label.
        """.trimIndent()

        val textOnlyContent = content { text(prompt) }
        val multimodalContent = if (inlineAttachments.isNotEmpty()) {
            content {
                inlineAttachments.forEach { attachment ->
                    inlineData(attachment.bytes, attachment.mimeType)
                }
                text(prompt)
            }
        } else {
            textOnlyContent
        }

        val rawJson = try {
            scanRawJsonWithModel(structuredQuizModel, multimodalContent, timeoutMs = 55_000L)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException) {
                Log.w(TAG, "Primary quiz model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                tryFallbackQuizGeneration(multimodalContent, textOnlyContent, inlineAttachments.isNotEmpty())
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            Log.w(TAG, "Primary quiz model failed; falling back to gemini-3.5-flash-lite", primaryError)
            tryFallbackQuizGeneration(multimodalContent, textOnlyContent, inlineAttachments.isNotEmpty())
        }

        val fallbackTitle = "$safeSubject • ${safeTopic.take(60)}"
        return parsePracticeQuizJson(rawJson, fallbackTitle = fallbackTitle, fallbackTopic = safeTopic)
    }

    private suspend fun tryFallbackQuizGeneration(
        multimodalContent: com.google.firebase.ai.type.Content,
        textOnlyContent: com.google.firebase.ai.type.Content,
        hasAttachments: Boolean
    ): String {
        return try {
            scanRawJsonWithFallback(fallbackQuizModel, multimodalContent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hasAttachments) {
                Log.w(TAG, "Fallback quiz generation with binary attachments failed; retrying with extracted notes/text context only", e)
                scanRawJsonWithFallback(fallbackQuizModel, textOnlyContent)
            } else {
                throw e
            }
        }
    }

    internal fun parsePracticeQuizJson(
        rawJson: String,
        fallbackTitle: String = "Practice Quiz",
        fallbackTopic: String = "General"
    ): Pair<String, List<QuizQuestion>> {
        val root = JSONObject(sanitizeJsonObject(rawJson))
        val title = root.optString("quizTitle").trim().ifBlank { fallbackTitle }
        val questionsArray = root.optJSONArray("questions") ?: JSONArray()
        val questions = mutableListOf<QuizQuestion>()
        for (i in 0 until questionsArray.length()) {
            val qObj = questionsArray.optJSONObject(i) ?: continue
            val qText = qObj.optString("questionText").trim()
            if (qText.isBlank()) continue
            val optsArray = qObj.optJSONArray("options") ?: JSONArray()
            val opts = mutableListOf<String>()
            for (j in 0 until optsArray.length()) {
                val opt = optsArray.optString(j).trim()
                if (opt.isNotBlank()) opts.add(opt)
            }
            if (opts.size < 2) continue
            val safeCorrectIdx = qObj.optInt("correctIndex", 0).coerceIn(0, opts.lastIndex)
            questions.add(
                QuizQuestion(
                    id = "gen_q_${i + 1}_${UUID.randomUUID().toString().take(6)}",
                    questionText = qText,
                    options = opts,
                    correctIndex = safeCorrectIdx,
                    explanation = qObj.optString("explanation").trim(),
                    topic = qObj.optString("topic").trim().ifBlank { fallbackTopic }
                )
            )
        }
        if (questions.isEmpty()) {
            throw IllegalStateException("AI could not generate valid quiz questions. Please try again with a clearer topic or materials.")
        }
        return Pair(title, questions)
    }

    suspend fun generateFlashcardsFromNotes(
        selectedText: String,
        titleHint: String = "",
        subject: String = "",
        cardCount: Int = 8
    ): Pair<String, List<FlashcardItem>> {
        val cleanedText = selectedText.trim().take(12_000)
        if (cleanedText.isBlank()) {
            throw IllegalArgumentException("Please select or enter note text to generate flashcards.")
        }
        val clampedCount = cardCount.coerceIn(3, 20)
        val prompt = """
            Create a focused deck of $clampedCount active-recall digital flashcards derived from the following university study notes.
            ${if (subject.isNotBlank()) "Subject: $subject" else ""}
            ${if (titleHint.isNotBlank()) "Note Title: $titleHint" else ""}
            
            Guidelines:
            - Make each card test one crisp concept, definition, formula, mechanism, or distinction present in the selected notes.
            - front: clear question, prompt, or term (concise and specific).
            - back: accurate, direct answer or explanation (easy to self-grade quickly).
            - hint: brief memory cue or keyword without giving away the full answer.
            - topic: short subtopic label.
            
            Selected Study Notes:
            \"\"\"
            $cleanedText
            \"\"\"
        """.trimIndent()

        val promptContent = content { text(prompt) }
        val rawJson = try {
            scanRawJsonWithModel(structuredFlashcardModel, promptContent, timeoutMs = 55_000L)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException) {
                Log.w(TAG, "Primary flashcard model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackFlashcardModel, promptContent)
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            if (isRetryableModelError(primaryError)) {
                Log.w(TAG, "Primary flashcard model busy/unavailable; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackFlashcardModel, promptContent)
            } else {
                Log.e(TAG, "Failed to generate flashcards from notes", primaryError)
                throw primaryError
            }
        }

        val fallbackTitle = titleHint.ifBlank {
            if (subject.isNotBlank()) "$subject Flashcards" else "Study Notes Flashcards"
        }
        return parseFlashcardsJson(rawJson, fallbackTitle = fallbackTitle, fallbackTopic = subject)
    }

    internal fun parseFlashcardsJson(
        jsonText: String,
        fallbackTitle: String = "Study Flashcards",
        fallbackTopic: String = ""
    ): Pair<String, List<FlashcardItem>> {
        val root = JSONObject(sanitizeJsonObject(jsonText))
        val deckTitle = root.optString("deckTitle").trim().ifBlank { fallbackTitle }
        val cardsArray = root.optJSONArray("cards") ?: JSONArray()
        val cards = mutableListOf<FlashcardItem>()
        for (i in 0 until cardsArray.length()) {
            val obj = cardsArray.optJSONObject(i) ?: continue
            val front = obj.optString("front").trim()
            val back = obj.optString("back").trim()
            if (front.isBlank() || back.isBlank()) continue
            val hint = obj.optString("hint").trim()
            val topic = obj.optString("topic").trim().ifBlank { fallbackTopic }
            cards.add(
                FlashcardItem(
                    id = "fc_${i + 1}_${UUID.randomUUID().toString().take(8)}",
                    front = front,
                    back = back,
                    hint = hint,
                    topic = topic,
                    isMastered = false,
                    reviewCount = 0
                )
            )
        }
        if (cards.isEmpty()) {
            throw IllegalStateException("AI could not extract flashcards from that selection. Try selecting more detailed notes.")
        }
        return Pair(deckTitle, cards)
    }

    suspend fun extractScheduleItemsFromImage(
        bitmap: Bitmap,
        targetType: String
    ): List<ExtractedScheduleEntry> {
        if (targetType == "CLASS") {
            return extractTimetableClassesFromImage(bitmap).map {
                ExtractedScheduleEntry(
                    title = it.title,
                    courseCode = it.courseCode,
                    dayOfWeek = it.dayOfWeek,
                    dueDate = "",
                    timeSlot = it.timeSlot,
                    notes = it.notes
                )
            }
        }

        val jpegBytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        }

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prompt = when (targetType) {
            "EXAM" -> """
                Extract every exam/test date from this exam schedule photo as JSON (reference date today: $todayIso).
                - title: exam or subject name.
                - courseCode: uppercase code if visible, else short abbreviation.
                - dueDate: date in YYYY-MM-DD format (infer year from $todayIso if omitted).
                - timeSlot: time range (e.g. "09:00 - 12:00 PM"); if only start time shown use it, else "09:00 - 12:00 PM".
                - dayOfWeek: weekday name if shown, else "".
                - notes: exam room/hall or syllabus topics if shown, else "".
                Skip headers and empty rows.
            """.trimIndent()
            else -> """
                Extract every syllabus milestone, assignment deadline, or project date from this photo as JSON (reference date today: $todayIso).
                - title: milestone, assignment, lab, or chapter deadline title.
                - courseCode: uppercase code if visible, else short abbreviation.
                - dueDate: due date in YYYY-MM-DD format (infer year from $todayIso if omitted).
                - timeSlot: due time or range (e.g. "05:00 PM" or "10:00 - 11:00 AM"); if omitted use "09:00 AM".
                - dayOfWeek: weekday name if shown, else "".
                - notes: chapters, topics, or deliverables if shown, else "".
                Skip headers and empty rows.
            """.trimIndent()
        }

        val promptContent = content {
            inlineData(jpegBytes, "image/jpeg")
            text(prompt)
        }

        val rawJson = try {
            scanRawJsonWithModel(structuredScheduleModel, promptContent, timeoutMs = 55_000L)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException) {
                Log.w(TAG, "Primary schedule model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackScheduleModel, promptContent)
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            if (isRetryableModelError(primaryError)) {
                Log.w(TAG, "Primary schedule model busy/unavailable; falling back to gemini-3.5-flash-lite", primaryError)
                scanRawJsonWithFallback(fallbackScheduleModel, promptContent)
            } else {
                Log.e(TAG, "Failed to extract schedule items from image", primaryError)
                throw primaryError
            }
        }

        return parseSchedulePhotoJson(rawJson, targetType)
    }

    suspend fun extractTimetableClassesFromImage(bitmap: Bitmap): List<ExtractedTimetableClass> {
        val jpegBytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        }

        val prompt = """
            Extract every scheduled class/lab from this timetable image as JSON.
            - title: concise subject name.
            - courseCode: uppercase code if visible, else short abbreviation.
            - dayOfWeek: Monday..Sunday (one entry per day if repeated).
            - timeSlot: time range (e.g. "09:00 - 10:00 AM").
            - notes: brief room/lab/instructor if shown, else "".
            Skip empty cells and breaks.
        """.trimIndent()

        val promptContent = content {
            inlineData(jpegBytes, "image/jpeg")
            text(prompt)
        }

        return try {
            scanWithModel(structuredTimetableModel, promptContent, timeoutMs = 55_000L)
        } catch (primaryError: CancellationException) {
            if (primaryError is TimeoutCancellationException) {
                Log.w(TAG, "Primary model timed out; falling back to gemini-3.5-flash-lite", primaryError)
                scanWithFallbackModel(promptContent)
            } else {
                throw primaryError
            }
        } catch (primaryError: Exception) {
            if (isRetryableModelError(primaryError)) {
                Log.w(TAG, "Primary model busy/unavailable; falling back to gemini-3.5-flash-lite", primaryError)
                scanWithFallbackModel(promptContent)
            } else {
                Log.e(TAG, "Failed to extract timetable from image", primaryError)
                throw primaryError
            }
        }
    }

    private suspend fun scanWithFallbackModel(
        promptContent: com.google.firebase.ai.type.Content
    ): List<ExtractedTimetableClass> {
        val jsonText = scanRawJsonWithFallback(fallbackTimetableModel, promptContent)
        return parseTimetableJson(jsonText)
    }

    private suspend fun scanRawJsonWithFallback(
        fallbackModel: GenerativeModel,
        promptContent: com.google.firebase.ai.type.Content
    ): String {
        return try {
            scanRawJsonWithModel(fallbackModel, promptContent, timeoutMs = 55_000L)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                throw IllegalStateException("Photo scan timed out. Please try cropping closer to the schedule.", e)
            }
            throw e
        } catch (e: Exception) {
            if (isRetryableModelError(e)) {
                Log.w(TAG, "Fallback model briefly busy; retrying once after 1.5s", e)
                delay(1500L)
                try {
                    scanRawJsonWithModel(fallbackModel, promptContent, timeoutMs = 55_000L)
                } catch (retryError: TimeoutCancellationException) {
                    throw IllegalStateException("Photo scan timed out. Please try again in a moment.", retryError)
                } catch (retryError: CancellationException) {
                    throw retryError
                }
            } else {
                Log.e(TAG, "Fallback schedule scan failed", e)
                throw e
            }
        }
    }

    private suspend fun scanWithModel(
        model: GenerativeModel,
        promptContent: com.google.firebase.ai.type.Content,
        timeoutMs: Long
    ): List<ExtractedTimetableClass> {
        return parseTimetableJson(scanRawJsonWithModel(model, promptContent, timeoutMs))
    }

    private suspend fun scanRawJsonWithModel(
        model: GenerativeModel,
        promptContent: com.google.firebase.ai.type.Content,
        timeoutMs: Long
    ): String {
        val accumulated = StringBuilder()
        withTimeout(timeoutMs) {
            model.generateContentStream(promptContent).collect { chunk ->
                chunk.text?.let { accumulated.append(it) }
            }
        }
        val jsonText = accumulated.toString()
        if (jsonText.isBlank()) {
            throw IllegalStateException("Model returned empty response.")
        }
        return jsonText
    }

    internal fun isRetryableModelError(e: Throwable): Boolean {
        if (e is TimeoutCancellationException) return true
        val msg = generateSequence(e) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .joinToString(" | ")
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("resource exhausted") ||
            msg.contains("resource_exhausted") ||
            msg.contains("quota") ||
            msg.contains("rate limit") ||
            msg.contains("too many requests") ||
            msg.contains("429") ||
            msg.contains("503") ||
            msg.contains("try again") ||
            msg.contains("internal")
    }

    internal fun sanitizeJsonObject(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }

    internal fun parseSchedulePhotoJson(jsonText: String, targetType: String): List<ExtractedScheduleEntry> {
        val root = JSONObject(sanitizeJsonObject(jsonText))
        val itemsArray = root.optJSONArray("items") ?: root.optJSONArray("classes") ?: JSONArray()
        val results = mutableListOf<ExtractedScheduleEntry>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isBlank()) continue
            val fallbackTag = when (targetType) {
                "EXAM" -> "EXAM"
                "MILESTONE" -> "TASK"
                else -> "CLASS"
            }
            val courseCode = obj.optString("courseCode").trim().uppercase().ifBlank {
                title.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(3)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifBlank { fallbackTag }
            }
            val day = obj.optString("dayOfWeek").trim().let {
                if (it.isBlank()) "Monday" else normalizeWeekday(it)
            }
            val dueDate = obj.optString("dueDate").trim().take(50)
            val defaultTime = when (targetType) {
                "EXAM" -> "09:00 - 12:00 PM"
                "MILESTONE" -> "09:00 AM"
                else -> "09:00 - 10:00 AM"
            }
            val timeSlot = obj.optString("timeSlot").trim().ifBlank { defaultTime }
            val notes = obj.optString("notes").trim()
            results.add(
                ExtractedScheduleEntry(
                    title = title,
                    courseCode = courseCode,
                    dayOfWeek = day,
                    dueDate = dueDate,
                    timeSlot = timeSlot,
                    notes = notes
                )
            )
        }
        return results
    }

    internal fun parseTimetableJson(jsonText: String): List<ExtractedTimetableClass> {
        val root = JSONObject(sanitizeJsonObject(jsonText))
        val classesArray = root.optJSONArray("classes") ?: JSONArray()
        val results = mutableListOf<ExtractedTimetableClass>()
        for (i in 0 until classesArray.length()) {
            val obj = classesArray.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isBlank()) continue
            val courseCode = obj.optString("courseCode").trim().uppercase().ifBlank {
                title.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(3)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifBlank { "CLASS" }
            }
            val day = normalizeWeekday(obj.optString("dayOfWeek"))
            val timeSlot = obj.optString("timeSlot").trim().ifBlank { "Scheduled Time" }
            val notes = obj.optString("notes").trim()
            results.add(
                ExtractedTimetableClass(
                    title = title,
                    courseCode = courseCode,
                    dayOfWeek = day,
                    timeSlot = timeSlot,
                    notes = notes
                )
            )
        }
        return results
    }

    private fun normalizeWeekday(raw: String): String {
        val cleaned = raw.trim().lowercase()
        return when {
            cleaned.startsWith("mon") -> "Monday"
            cleaned.startsWith("tue") -> "Tuesday"
            cleaned.startsWith("wed") -> "Wednesday"
            cleaned.startsWith("thu") -> "Thursday"
            cleaned.startsWith("fri") -> "Friday"
            cleaned.startsWith("sat") -> "Saturday"
            cleaned.startsWith("sun") -> "Sunday"
            else -> raw.trim().ifBlank { "Monday" }
        }
    }
}
