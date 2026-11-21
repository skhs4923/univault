package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Quiz
import com.example.data.model.QuizQuestion
import com.example.data.model.ScheduleItem
import com.example.data.model.StudentAnswerRecord
import com.example.data.model.StudyMaterial
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.AiTutorViewModel
import com.example.ui.viewmodel.FlashcardViewModel
import com.example.ui.viewmodel.QuizViewModel
import com.example.util.VaultMaterialAnalyzer

@Composable
fun QuizScreen(
    quizViewModel: QuizViewModel,
    aiTutorViewModel: AiTutorViewModel,
    flashcardViewModel: FlashcardViewModel,
    availableNotes: List<StudyMaterial> = emptyList(),
    schedules: List<ScheduleItem> = emptyList()
) {
    val activeQuizState by quizViewModel.activeQuizState.collectAsState()
    val quizzes by quizViewModel.quizzes.collectAsState()
    val isGenerating by quizViewModel.isGenerating.collectAsState()
    val statusMsg by quizViewModel.statusMessage.collectAsState()
    val perfMetrics by quizViewModel.performanceMetrics.collectAsState()
    val mistakeExplanation by aiTutorViewModel.mistakeExplanation.collectAsState()

    val flashcardDecks by flashcardViewModel.decks.collectAsState()
    val activeFlashcardSession by flashcardViewModel.activeSession.collectAsState()
    val isGeneratingDeck by flashcardViewModel.isGenerating.collectAsState()
    val flashcardStatusMsg by flashcardViewModel.statusMessage.collectAsState()
    val isFlashcardError by flashcardViewModel.isStatusError.collectAsState()

    var selectedHubTab by remember { mutableIntStateOf(0) } // 0: Practice Tests, 1: Flashcard Decks
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAiGenDialog by remember { mutableStateOf(false) }
    var selectedMistakeRecord by remember { mutableStateOf<StudentAnswerRecord?>(null) }

    val sessionSnapshot = activeFlashcardSession
    if (sessionSnapshot != null) {
        FlashcardReviewView(
            session = sessionSnapshot,
            onClose = { flashcardViewModel.closeSession() },
            onFlip = { flashcardViewModel.flipCard() },
            onToggleHint = { flashcardViewModel.toggleHint() },
            onNext = { flashcardViewModel.nextCard() },
            onPrevious = { flashcardViewModel.previousCard() },
            onShuffle = { flashcardViewModel.shuffleSession() },
            onToggleUnmasteredOnly = { flashcardViewModel.toggleUnmasteredFilter() },
            onMarkMastered = { mastered -> flashcardViewModel.markCardMastered(mastered) },
            onRestart = { unmasteredOnly -> flashcardViewModel.restartSession(unmasteredOnly) },
            onResetProgress = { flashcardViewModel.resetDeckProgress() },
            onAddCard = { front, back, hint -> flashcardViewModel.addCardToSessionDeck(front, back, hint) }
        )
    } else if (activeQuizState.quiz != null && !activeQuizState.isFinished) {
        // ACTIVE QUIZ RUNNER
        ActiveQuizRunner(
            state = activeQuizState,
            onSelectOption = { qIdx, optIdx -> quizViewModel.selectOption(qIdx, optIdx) },
            onNext = { quizViewModel.nextQuestion() },
            onPrevious = { quizViewModel.previousQuestion() },
            onSubmit = { quizViewModel.submitQuiz() },
            onExit = { quizViewModel.exitQuiz() }
        )
    } else if (activeQuizState.isFinished && activeQuizState.evaluatedResult != null) {
        // QUIZ RESULT & MISTAKE REVIEW
        QuizResultView(
            result = activeQuizState.evaluatedResult!!,
            records = activeQuizState.answerRecords,
            onDone = { quizViewModel.exitQuiz() },
            onExplainMistake = { record ->
                selectedMistakeRecord = record
                aiTutorViewModel.explainMistake(activeQuizState.evaluatedResult!!.subject, record)
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedHubTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedHubTab == 0,
                    onClick = { selectedHubTab = 0 },
                    modifier = Modifier.testTag("quiz_hub_tests_tab"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Practice Tests",
                                fontWeight = if (selectedHubTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedHubTab == 1,
                    onClick = { selectedHubTab = 1 },
                    modifier = Modifier.testTag("quiz_hub_flashcards_tab"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Flashcard Decks",
                                fontWeight = if (selectedHubTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
            }

            if (selectedHubTab == 1) {
                FlashcardsTabContent(
                    decks = flashcardDecks,
                    availableNotes = availableNotes,
                    isGenerating = isGeneratingDeck,
                    statusMessage = flashcardStatusMsg,
                    isStatusError = isFlashcardError,
                    onDismissStatus = { flashcardViewModel.clearStatusMessage() },
                    onGenerateDeck = { text, title, subj, code, noteId, noteTitle, count ->
                        flashcardViewModel.generateDeckFromNotes(
                            selectedText = text,
                            deckTitle = title,
                            subject = subj,
                            courseCode = code,
                            sourceNoteId = noteId,
                            sourceNoteTitle = noteTitle,
                            cardCount = count,
                            openImmediately = true
                        )
                    },
                    onOpenDeck = { deck, unmasteredOnly ->
                        flashcardViewModel.openDeck(deck, unmasteredOnly = unmasteredOnly)
                    },
                    onDeleteDeck = { deck -> flashcardViewModel.deleteDeck(deck) }
                )
            } else {
                // QUIZZES LIST & CREATION HUB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Custom Tests & Quizzes",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Test your knowledge with custom marking schemes (+/- marks) and get instant AI mistake reviews.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f).testTag("create_custom_quiz_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Custom Test", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showAiGenDialog = true },
                            modifier = Modifier.weight(1f).testTag("generate_ai_quiz_btn"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGenerating
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Generate with AI", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            statusMsg?.let { msg ->
                val isErr = msg.contains("error", ignoreCase = true) || msg.contains("failed", ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("quiz_status_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isErr) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { quizViewModel.clearStatusMessage() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notice",
                                tint = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (isGenerating) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AI is analyzing your Vault files, question banks, question papers & notes to build your quiz...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Text(
                "Available Practice Tests",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (quizzes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No tests created yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap 'Generate with AI' to automatically generate a practice quiz on any subject!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(quizzes, key = { it.id }) { quiz ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(quiz.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        "${quiz.subject} • ${quiz.courseCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Marking Scheme: +${quiz.positiveMarks} / -${quiz.negativeMarks} • ${quiz.questionsCount} Questions • ${quiz.durationMinutes} mins",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { quizViewModel.startQuiz(quiz) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Test")
                                }
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }

    // Modal: AI Mistake Explanation Sheet
    selectedMistakeRecord?.let { record ->
        AlertDialog(
            onDismissRequest = {
                selectedMistakeRecord = null
                aiTutorViewModel.clearMistakeExplanation()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Tutor Mistake Analysis", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Question: ${record.questionText}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Your Answer: Option #${record.selectedIndex + 1}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Text("Correct Answer: Option #${record.correctIndex + 1}", color = Color(0xFF16A34A), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    when (val state = mistakeExplanation) {
                        is AiTaskState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Analyzing your test metrics with Gemini AI...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is AiTaskState.Success -> {
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                        is AiTaskState.Error -> {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedMistakeRecord = null
                    aiTutorViewModel.clearMistakeExplanation()
                }) {
                    Text("Understood")
                }
            }
        )
    }

    // Modal: Generate AI Practice Quiz (Topic Selection + Full Vault Files/Notes/Banks/Papers Analysis)
    if (showAiGenDialog) {
        AiQuizGeneratorDialog(
            availableMaterials = availableNotes,
            schedules = schedules,
            weakTopics = perfMetrics.weakTopicList,
            isGenerating = isGenerating,
            onDismiss = { showAiGenDialog = false },
            onConfirm = { subject, courseCode, topic, pos, neg, count, difficulty, selectedMaterials, startImmediately ->
                quizViewModel.generateAiPracticeQuiz(
                    subject = subject,
                    courseCode = courseCode,
                    topic = topic,
                    positiveMarks = pos,
                    negativeMarks = neg,
                    count = count,
                    difficulty = difficulty,
                    sourceMaterials = selectedMaterials,
                    startImmediately = startImmediately
                )
                showAiGenDialog = false
            }
        )
    }

    // Modal: Create Custom Quiz
    if (showCreateDialog) {
        CreateCustomQuizDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, subject, code, pos, neg, duration, questions ->
                quizViewModel.createCustomQuiz(title, subject, code, pos, neg, duration, questions)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ActiveQuizRunner(
    state: com.example.ui.viewmodel.ActiveQuizState,
    onSelectOption: (qIdx: Int, optIdx: Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSubmit: () -> Unit,
    onExit: () -> Unit
) {
    val quiz = state.quiz ?: return
    val currentQ = state.questions.getOrNull(state.currentQuestionIndex) ?: return
    val selectedOption = state.userAnswers[state.currentQuestionIndex] ?: -1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Quiz Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(quiz.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Question ${state.currentQuestionIndex + 1} of ${state.questions.size} • Marking: +${quiz.positiveMarks} / -${quiz.negativeMarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = "Exit Test")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { (state.currentQuestionIndex + 1).toFloat() / state.questions.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Question Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = currentQ.questionText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    lineHeight = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options List
        currentQ.options.forEachIndexed { optIdx, optText ->
            val isSelected = selectedOption == optIdx
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectOption(state.currentQuestionIndex, optIdx) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectOption(state.currentQuestionIndex, optIdx) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Navigation & Submit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = state.currentQuestionIndex > 0
            ) {
                Text("Previous")
            }

            if (state.currentQuestionIndex < state.questions.size - 1) {
                Button(onClick = onNext) {
                    Text("Next Question")
                }
            } else {
                Button(
                    onClick = onSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Submit Test")
                }
            }
        }
    }
}

@Composable
fun QuizResultView(
    result: com.example.data.model.TestResult,
    records: List<StudentAnswerRecord>,
    onDone: () -> Unit,
    onExplainMistake: (StudentAnswerRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Result Score Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Test Report & Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${result.score.toInt()} / ${result.totalMarks.toInt()}",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Accuracy: ${result.accuracy.toInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(result.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Answer Sheet & Mistake Review",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Verify your mistakes against the answer key. Tap 'Explain with AI' to get conceptual clarification.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        records.forEachIndexed { i, record ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (record.isCorrect) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (record.isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (record.isCorrect) "CORRECT" else "INCORRECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Question ${i + 1}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(record.questionText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.Black)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Your answer: Option #${record.selectedIndex + 1}", color = if (record.isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                    Text("Correct key: Option #${record.correctIndex + 1}", color = Color(0xFF16A34A), style = MaterialTheme.typography.bodySmall)

                    if (record.explanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Explanation: ${record.explanation}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF374151))
                    }

                    if (!record.isCorrect) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onExplainMistake(record) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explain Mistake with AI Tutor", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Done Reviewing")
        }
    }
}

@Composable
fun CreateCustomQuizDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        subject: String,
        courseCode: String,
        positiveMarks: Int,
        negativeMarks: Double,
        durationMinutes: Int,
        questions: List<QuizQuestion>
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var positiveMarks by remember { mutableStateOf("4") }
    var negativeMarks by remember { mutableStateOf("1.0") }
    var durationMinutes by remember { mutableStateOf("20") }

    // Sample default questions builder for fast test creation
    var q1Text by remember { mutableStateOf("What is the worst-case time complexity of QuickSort?") }
    var q1OptA by remember { mutableStateOf("O(n log n)") }
    var q1OptB by remember { mutableStateOf("O(n^2)") }
    var q1OptC by remember { mutableStateOf("O(n)") }
    var q1OptD by remember { mutableStateOf("O(log n)") }
    var q1Correct by remember { mutableStateOf(1) } // O(n^2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Test Pattern & Marking Scheme") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Test Title *") }, placeholder = { Text("CS201 Unit 2 Quick Assessment") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = courseCode, onValueChange = { courseCode = it }, label = { Text("Course Code") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = positiveMarks, onValueChange = { positiveMarks = it }, label = { Text("+ Marks per Q") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = negativeMarks, onValueChange = { negativeMarks = it }, label = { Text("- Marks Penalty") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = durationMinutes, onValueChange = { durationMinutes = it }, label = { Text("Minutes") }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Sample Question Setup:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                OutlinedTextField(value = q1Text, onValueChange = { q1Text = it }, label = { Text("Question 1") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = q1OptA, onValueChange = { q1OptA = it }, label = { Text("Option 1") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = q1OptB, onValueChange = { q1OptB = it }, label = { Text("Option 2 (Correct)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = q1OptC, onValueChange = { q1OptC = it }, label = { Text("Option 3") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = q1OptD, onValueChange = { q1OptD = it }, label = { Text("Option 4") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pos = positiveMarks.toIntOrNull() ?: 4
                    val neg = negativeMarks.toDoubleOrNull() ?: 1.0
                    val dur = durationMinutes.toIntOrNull() ?: 20
                    val questions = listOf(
                        QuizQuestion(
                            id = "q1",
                            questionText = q1Text,
                            options = listOf(q1OptA, q1OptB, q1OptC, q1OptD),
                            correctIndex = q1Correct,
                            explanation = "QuickSort worst case happens when the pivot selection results in unbalanced partitions (e.g. already sorted array with first element as pivot), yielding O(n^2).",
                            topic = "Sorting Algorithms"
                        )
                    )
                    onConfirm(title, subject, courseCode, pos, neg, dur, questions)
                },
                enabled = title.isNotBlank() && q1Text.isNotBlank()
            ) {
                Text("Save Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AiQuizGeneratorDialog(
    availableMaterials: List<StudyMaterial>,
    schedules: List<ScheduleItem>,
    weakTopics: List<String>,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        subject: String,
        courseCode: String,
        topic: String,
        positiveMarks: Int,
        negativeMarks: Double,
        count: Int,
        difficulty: String,
        selectedMaterials: List<StudyMaterial>,
        startImmediately: Boolean
    ) -> Unit
) {
    val defaultSubject = remember(availableMaterials) {
        availableMaterials.firstOrNull { it.subject.isNotBlank() }?.subject ?: "Computer Science"
    }
    val defaultCode = remember(availableMaterials) {
        availableMaterials.firstOrNull { it.courseCode.isNotBlank() }?.courseCode ?: "CS201"
    }
    val topicCandidates = remember(availableMaterials, schedules, weakTopics) {
        buildList {
            add("All Selected Vault Materials")
            addAll(VaultMaterialAnalyzer.extractSelectableTopics(availableMaterials, schedules, weakTopics))
        }.distinct()
    }
    val subjectCandidates = remember(availableMaterials) {
        availableMaterials.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct().take(10)
    }

    var subject by remember { mutableStateOf(defaultSubject) }
    var courseCode by remember { mutableStateOf(defaultCode) }
    var topic by remember { mutableStateOf(topicCandidates.getOrNull(1) ?: topicCandidates.firstOrNull() ?: "Core Concepts") }
    var analyzeAllVault by remember { mutableStateOf(true) }
    val selectedMaterialIds = remember(availableMaterials) {
        mutableStateListOf<String>().apply {
            availableMaterials.forEach { if (it.id.isNotBlank()) add(it.id) }
        }
    }
    var questionCount by remember { mutableIntStateOf(5) }
    var difficulty by remember { mutableStateOf("Exam-Level") }
    var positiveMarks by remember { mutableStateOf("4") }
    var negativeMarks by remember { mutableStateOf("1.0") }
    var startImmediately by remember { mutableStateOf(true) }

    val notesCount = remember(availableMaterials) {
        availableMaterials.count { it.type != "QUESTION_BANK" && it.type != "QUESTION_PAPER" }
    }
    val banksCount = remember(availableMaterials) {
        availableMaterials.count { it.type == "QUESTION_BANK" }
    }
    val papersCount = remember(availableMaterials) {
        availableMaterials.count { it.type == "QUESTION_PAPER" }
    }
    val filesCount = remember(availableMaterials) {
        availableMaterials.count { it.fileName.isNotBlank() || it.fileBase64.isNotBlank() || it.hasChunks }
    }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Quiz from Vault & Topics", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Select Topic Chips + Custom Topic Input
                Text(
                    text = "1. Select or Enter Topic Focus *",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topicCandidates.forEach { candidate ->
                        FilterChip(
                            selected = topic.equals(candidate, ignoreCase = true),
                            onClick = { topic = candidate },
                            label = {
                                Text(
                                    text = candidate.take(28),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.testTag("ai_quiz_topic_chip_${candidate.take(12)}")
                        )
                    }
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Syllabus Unit *") },
                    placeholder = { Text("Tap a topic chip above or type any topic...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_quiz_topic_input")
                )

                // 2. Vault Materials Analysis (Files, Question Banks, Question Papers, Notes)
                Text(
                    text = "2. Analyze Vault Materials (${availableMaterials.size} available)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "$notesCount Notes • $banksCount Question Banks • $papersCount Question Papers • $filesCount Attached Files",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (availableMaterials.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = analyzeAllVault,
                                    onClick = { analyzeAllVault = true },
                                    label = { Text("Analyze All (${availableMaterials.size})", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("ai_quiz_analyze_all_chip")
                                )
                                FilterChip(
                                    selected = !analyzeAllVault,
                                    onClick = { analyzeAllVault = false },
                                    label = { Text("Pick Specific (${selectedMaterialIds.size})", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("ai_quiz_pick_specific_chip")
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Upload PDFs, notes, question banks, or question papers in Vault to ground questions directly in your files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!analyzeAllVault && availableMaterials.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableMaterials.forEach { mat ->
                            val checked = mat.id in selectedMaterialIds
                            val badge = when (mat.type) {
                                "QUESTION_PAPER" -> "Q-PAPER"
                                "QUESTION_BANK" -> "Q-BANK"
                                else -> "NOTE"
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedMaterialIds.remove(mat.id)
                                        else selectedMaterialIds.add(mat.id)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                if (mat.id !in selectedMaterialIds) selectedMaterialIds.add(mat.id)
                                            } else {
                                                selectedMaterialIds.remove(mat.id)
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mat.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "$badge • ${mat.courseCode.ifBlank { mat.subject }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (mat.fileName.isNotBlank()) {
                                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Text(
                                                    text = mat.fileName.take(18),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else if (mat.content.isNotBlank()) {
                                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Text(
                                                    text = "Text Notes",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Subject & Course Code
                if (subjectCandidates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subjectCandidates.forEach { subj ->
                            FilterChip(
                                selected = subject.equals(subj, ignoreCase = true),
                                onClick = {
                                    subject = subj
                                    availableMaterials.firstOrNull { it.subject.equals(subj, ignoreCase = true) }?.let {
                                        if (it.courseCode.isNotBlank()) courseCode = it.courseCode
                                    }
                                },
                                label = { Text(subj, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Code") },
                        modifier = Modifier.weight(0.7f)
                    )
                }

                // 4. Question Count & Difficulty
                Text(
                    text = "Questions & Difficulty",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15).forEach { cnt ->
                        FilterChip(
                            selected = questionCount == cnt,
                            onClick = { questionCount = cnt },
                            label = { Text("$cnt Qs", fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Standard", "Exam-Level", "Challenging").forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(diff, fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = positiveMarks,
                        onValueChange = { positiveMarks = it },
                        label = { Text("+ Marks") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = negativeMarks,
                        onValueChange = { negativeMarks = it },
                        label = { Text("- Penalty") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start quiz automatically once ready",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = startImmediately,
                        onCheckedChange = { startImmediately = it }
                    )
                }
            }
        },
        confirmButton = {
            val chosenMaterials = if (analyzeAllVault) {
                availableMaterials
            } else {
                availableMaterials.filter { it.id in selectedMaterialIds }
            }
            Button(
                onClick = {
                    val pos = positiveMarks.toIntOrNull() ?: 4
                    val neg = negativeMarks.toDoubleOrNull() ?: 1.0
                    onConfirm(
                        subject,
                        courseCode,
                        topic,
                        pos,
                        neg,
                        questionCount,
                        difficulty,
                        chosenMaterials,
                        startImmediately
                    )
                },
                enabled = topic.isNotBlank() && !isGenerating,
                modifier = Modifier.testTag("confirm_generate_ai_quiz_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Quiz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) { Text("Cancel") }
        }
    )
}
