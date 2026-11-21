package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Quiz
import com.example.data.model.QuizQuestion
import com.example.data.model.StudentAnswerRecord
import com.example.data.model.StudyMaterial
import com.example.data.model.TestResult
import com.example.data.repository.AiTutorRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.StudyMaterialRepository
import com.example.util.VaultMaterialAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveQuizState(
    val quiz: Quiz? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val userAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> selectedOptionIndex
    val isFinished: Boolean = false,
    val evaluatedResult: TestResult? = null,
    val answerRecords: List<StudentAnswerRecord> = emptyList()
)

data class PerformanceMetrics(
    val testsTaken: Int = 0,
    val averageScorePct: Double = 0.0,
    val highestScorePct: Double = 0.0,
    val subjectPerformance: Map<String, Double> = emptyMap(), // Subject -> Avg Accuracy
    val weakTopicList: List<String> = emptyList()
)

class QuizViewModel(
    private val quizRepository: QuizRepository,
    private val aiTutorRepository: AiTutorRepository,
    private val studyMaterialRepository: StudyMaterialRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _activeQuizState = MutableStateFlow(ActiveQuizState())
    val activeQuizState: StateFlow<ActiveQuizState> = _activeQuizState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val quizzes: StateFlow<List<Quiz>> = quizRepository.observeQuizzes()
        .map { list -> list.sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val testResults: StateFlow<List<TestResult>> = quizRepository.observeTestResults()
        .map { list -> list.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val performanceMetrics: StateFlow<PerformanceMetrics> = testResults.map { list ->
        if (list.isEmpty()) {
            PerformanceMetrics()
        } else {
            val totalTaken = list.size
            val avgAcc = list.map { it.accuracy }.average()
            val maxAcc = list.maxOfOrNull { it.accuracy } ?: 0.0

            val bySubject = list.groupBy { it.subject }
                .mapValues { entry -> entry.value.map { it.accuracy }.average() }

            val allWeak = list.mapNotNull { it.weakTopics.ifBlank { null } }
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            PerformanceMetrics(
                testsTaken = totalTaken,
                averageScorePct = avgAcc,
                highestScorePct = maxAcc,
                subjectPerformance = bySubject,
                weakTopicList = allWeak
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PerformanceMetrics()
    )

    fun startQuiz(quiz: Quiz) {
        val questions = QuizQuestion.parseList(quiz.questionsJson)
        _activeQuizState.value = ActiveQuizState(
            quiz = quiz,
            questions = questions,
            currentQuestionIndex = 0,
            userAnswers = emptyMap(),
            isFinished = false,
            evaluatedResult = null,
            answerRecords = emptyList()
        )
    }

    fun selectOption(questionIndex: Int, optionIndex: Int) {
        val currentAnswers = _activeQuizState.value.userAnswers.toMutableMap()
        currentAnswers[questionIndex] = optionIndex
        _activeQuizState.value = _activeQuizState.value.copy(userAnswers = currentAnswers)
    }

    fun nextQuestion() {
        val state = _activeQuizState.value
        if (state.currentQuestionIndex < state.questions.size - 1) {
            _activeQuizState.value = state.copy(currentQuestionIndex = state.currentQuestionIndex + 1)
        }
    }

    fun previousQuestion() {
        val state = _activeQuizState.value
        if (state.currentQuestionIndex > 0) {
            _activeQuizState.value = state.copy(currentQuestionIndex = state.currentQuestionIndex - 1)
        }
    }

    fun submitQuiz() {
        val state = _activeQuizState.value
        val quiz = state.quiz ?: return
        val questions = state.questions

        var correctCount = 0
        var wrongCount = 0
        var unattemptedCount = 0
        val answerRecords = mutableListOf<StudentAnswerRecord>()
        val weakTopics = mutableSetOf<String>()

        questions.forEachIndexed { i, q ->
            val userPick = state.userAnswers[i] ?: -1
            val isCorrect = userPick == q.correctIndex
            if (userPick == -1) {
                unattemptedCount++
            } else if (isCorrect) {
                correctCount++
            } else {
                wrongCount++
                if (q.topic.isNotBlank()) weakTopics.add(q.topic)
            }

            answerRecords.add(
                StudentAnswerRecord(
                    questionId = q.id.ifBlank { "q_${i + 1}" },
                    questionText = q.questionText,
                    selectedIndex = userPick,
                    correctIndex = q.correctIndex,
                    isCorrect = isCorrect,
                    explanation = q.explanation,
                    topic = q.topic
                )
            )
        }

        val totalPossibleMarks = questions.size * quiz.positiveMarks.toDouble()
        val earnedScore = (correctCount * quiz.positiveMarks.toDouble()) - (wrongCount * quiz.negativeMarks)
        val finalScore = maxOf(0.0, earnedScore)
        val accuracyPct = if (totalPossibleMarks > 0) (finalScore / totalPossibleMarks) * 100.0 else 0.0

        val summaryText = "Completed: $correctCount Correct, $wrongCount Incorrect, $unattemptedCount Unattempted. Scoring Pattern: +${quiz.positiveMarks} / -${quiz.negativeMarks}."

        val result = TestResult(
            quizId = quiz.id,
            quizTitle = quiz.title.take(200),
            subject = quiz.subject.ifBlank { "General Studies" }.take(100),
            score = finalScore,
            totalMarks = totalPossibleMarks,
            accuracy = accuracyPct,
            summary = summaryText,
            answersJson = StudentAnswerRecord.serializeList(answerRecords),
            weakTopics = weakTopics.joinToString(", ").take(1000)
        )

        _activeQuizState.value = state.copy(
            isFinished = true,
            evaluatedResult = result,
            answerRecords = answerRecords
        )

        viewModelScope.launch {
            try {
                quizRepository.saveTestResult(result)
                _statusMessage.value = "Test submitted! Score: ${finalScore.toInt()} / ${totalPossibleMarks.toInt()}"
            } catch (e: Exception) {
                _statusMessage.value = "Result calculated, cloud sync notice: ${e.localizedMessage}"
            }
        }
    }

    fun exitQuiz() {
        _activeQuizState.value = ActiveQuizState()
    }

    fun createCustomQuiz(
        title: String,
        subject: String,
        courseCode: String,
        positiveMarks: Int,
        negativeMarks: Double,
        durationMinutes: Int,
        questions: List<QuizQuestion>
    ) {
        viewModelScope.launch {
            try {
                val safeTitle = title.trim().ifBlank { "Custom Practice Test" }.take(200)
                val safeSubject = subject.trim().ifBlank { "General Studies" }.take(100)
                val safeCode = courseCode.trim().uppercase().ifBlank { "GEN" }.take(50)
                val quiz = Quiz(
                    title = safeTitle,
                    subject = safeSubject,
                    courseCode = safeCode,
                    positiveMarks = positiveMarks.coerceAtLeast(1),
                    negativeMarks = negativeMarks.coerceAtLeast(0.0),
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    questionsCount = questions.size,
                    questionsJson = QuizQuestion.serializeList(questions)
                )
                quizRepository.createQuiz(quiz)
                _statusMessage.value = "Created test \"$safeTitle\" with custom marking scheme."
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create quiz: ${e.localizedMessage}"
            }
        }
    }

    fun generateAiPracticeQuiz(
        subject: String,
        courseCode: String,
        topic: String,
        positiveMarks: Int = 4,
        negativeMarks: Double = 1.0,
        count: Int = 5,
        difficulty: String = "Exam-Level",
        sourceMaterials: List<StudyMaterial> = emptyList(),
        startImmediately: Boolean = false
    ) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            _statusMessage.value = null
            try {
                val safeSubject = subject.trim().ifBlank {
                    sourceMaterials.firstOrNull { it.subject.isNotBlank() }?.subject ?: "Academic Studies"
                }.take(100)
                val safeCode = courseCode.trim().uppercase().ifBlank {
                    sourceMaterials.firstOrNull { it.courseCode.isNotBlank() }?.courseCode?.uppercase() ?: "QUIZ"
                }.take(50)
                val safeTopic = topic.trim().ifBlank { "Comprehensive Vault Materials Review" }

                val vaultContext = VaultMaterialAnalyzer.buildVaultQuizContext(
                    context = appContext,
                    repository = studyMaterialRepository,
                    materials = sourceMaterials,
                    selectedTopic = safeTopic
                )

                val (rawQuizTitle, generatedQuestions) = aiTutorRepository.generatePracticeQuiz(
                    subject = safeSubject,
                    courseCode = safeCode,
                    topic = safeTopic,
                    questionCount = count,
                    difficulty = difficulty,
                    sourceContextPrompt = vaultContext.promptText,
                    inlineAttachments = vaultContext.inlineAttachments
                )

                val safeQuizTitle = rawQuizTitle.trim().ifBlank { "$safeSubject • ${safeTopic.take(60)}" }.take(200)
                val quiz = Quiz(
                    title = safeQuizTitle,
                    subject = safeSubject,
                    courseCode = safeCode,
                    positiveMarks = positiveMarks.coerceAtLeast(1),
                    negativeMarks = negativeMarks.coerceAtLeast(0.0),
                    durationMinutes = (generatedQuestions.size * 3).coerceAtLeast(5),
                    questionsCount = generatedQuestions.size,
                    questionsJson = QuizQuestion.serializeList(generatedQuestions)
                )
                val createdId = quizRepository.createQuiz(quiz)
                val persistedQuiz = quiz.copy(id = createdId)

                val sourceInfo = if (vaultContext.analyzedCount > 0) {
                    " (analyzed ${vaultContext.analyzedCount} Vault items" +
                        (if (vaultContext.fileAttachmentsIncluded > 0) ", ${vaultContext.fileAttachmentsIncluded} files" else "") +
                        ")"
                } else {
                    ""
                }
                _statusMessage.value = "AI quiz \"$safeQuizTitle\" ready$sourceInfo!"
                if (startImmediately) {
                    startQuiz(persistedQuiz)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = "AI quiz generation error: ${e.localizedMessage ?: "Failed to generate quiz"}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
