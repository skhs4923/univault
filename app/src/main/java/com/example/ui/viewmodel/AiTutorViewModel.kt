package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.StudentAnswerRecord
import com.example.data.repository.AiTutorRepository
import com.example.util.WorkspaceAnalysisSnapshot
import com.example.util.WorkspaceContextBuilder
import com.example.util.WorkspaceFocusMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String = "user", // "user" or "ai"
    val text: String = "",
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Success(val text: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

class AiTutorViewModel(
    private val aiRepository: AiTutorRepository
) : ViewModel() {

    private fun welcomeMessage() = ChatMessage(
        sender = "ai",
        text = "Hello! I am your UniVault Academic AI Tutor. I can see your Vault notes & files, Calendar schedule, Progress metrics, Quizzes, Flashcards, and Campus Discussions. Ask me anything about your courses or open 'Workspace Insights' for a full 360° audit!"
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(welcomeMessage()))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _assignmentFeedback = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val assignmentFeedback: StateFlow<AiTaskState> = _assignmentFeedback.asStateFlow()

    private val _mistakeExplanation = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val mistakeExplanation: StateFlow<AiTaskState> = _mistakeExplanation.asStateFlow()

    private val _workspaceSnapshot = MutableStateFlow(WorkspaceAnalysisSnapshot())
    val workspaceSnapshot: StateFlow<WorkspaceAnalysisSnapshot> = _workspaceSnapshot.asStateFlow()

    private val _useWorkspaceContext = MutableStateFlow(true)
    val useWorkspaceContext: StateFlow<Boolean> = _useWorkspaceContext.asStateFlow()

    private val _workspaceAnalysis = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val workspaceAnalysis: StateFlow<AiTaskState> = _workspaceAnalysis.asStateFlow()

    fun updateWorkspaceSnapshot(snapshot: WorkspaceAnalysisSnapshot) {
        _workspaceSnapshot.value = snapshot
    }

    fun setUseWorkspaceContext(enabled: Boolean) {
        _useWorkspaceContext.value = enabled
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isStreaming.value) return

        val priorHistory = _messages.value
            .drop(1) // omit static greeting
            .filter { !it.isError && it.text.isNotBlank() && it.text != "Thinking..." }
            .map { it.sender to it.text }

        val contextBlock = if (_useWorkspaceContext.value) {
            WorkspaceContextBuilder.buildContextPrompt(_workspaceSnapshot.value)
        } else {
            ""
        }

        val userMsg = ChatMessage(sender = "user", text = trimmed)
        val initialAiMsg = ChatMessage(sender = "ai", text = "Thinking...")
        val aiMsgId = initialAiMsg.id

        _messages.value = _messages.value + userMsg + initialAiMsg
        _isStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepository.streamChatResponse(
                    prompt = trimmed,
                    history = priorHistory,
                    workspaceContext = contextBlock
                ).collect { streamedContent ->
                    if (streamedContent.isNotBlank()) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(text = streamedContent, isError = false) else msg
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val friendlyError = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "AI Tutor could not respond right now. Please try again."
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == aiMsgId) {
                        msg.copy(
                            text = "Could not complete response: $friendlyError",
                            isError = true
                        )
                    } else {
                        msg
                    }
                }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun analyzeWorkspace(
        focusMode: WorkspaceFocusMode = WorkspaceFocusMode.FULL_AUDIT,
        includeVault: Boolean = true,
        includeCalendar: Boolean = true,
        includeProgress: Boolean = true,
        includeDiscuss: Boolean = true,
        includeQuizzes: Boolean = true,
        customGoal: String = ""
    ) {
        if (!includeVault && !includeCalendar && !includeProgress && !includeDiscuss && !includeQuizzes) {
            _workspaceAnalysis.value = AiTaskState.Error("Select at least one workspace data source to analyze.")
            return
        }

        val contextPrompt = WorkspaceContextBuilder.buildContextPrompt(
            snapshot = _workspaceSnapshot.value,
            includeVault = includeVault,
            includeCalendar = includeCalendar,
            includeProgress = includeProgress,
            includeDiscuss = includeDiscuss,
            includeQuizzes = includeQuizzes
        )

        _workspaceAnalysis.value = AiTaskState.Loading
        viewModelScope.launch {
            try {
                val report = aiRepository.analyzeWorkspaceData(
                    workspaceContext = contextPrompt,
                    focusModeLabel = focusMode.label,
                    focusInstruction = focusMode.description,
                    customGoal = customGoal
                )
                _workspaceAnalysis.value = AiTaskState.Success(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _workspaceAnalysis.value = AiTaskState.Error(
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Workspace analysis failed. Please try again."
                )
            }
        }
    }

    fun clearWorkspaceAnalysis() {
        _workspaceAnalysis.value = AiTaskState.Idle
    }

    fun clearChat() {
        if (_isStreaming.value) return
        _messages.value = listOf(welcomeMessage())
    }

    fun submitAssignmentForReview(title: String, course: String, content: String) {
        if (content.isBlank()) return
        _assignmentFeedback.value = AiTaskState.Loading
        viewModelScope.launch {
            try {
                val feedback = aiRepository.reviewAssignment(title, course, content)
                _assignmentFeedback.value = AiTaskState.Success(feedback)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _assignmentFeedback.value = AiTaskState.Error(e.localizedMessage ?: "Review failed")
            }
        }
    }

    fun explainMistake(subject: String, record: StudentAnswerRecord) {
        _mistakeExplanation.value = AiTaskState.Loading
        viewModelScope.launch {
            try {
                val selectedText = "Option index ${record.selectedIndex + 1}"
                val correctText = "Option index ${record.correctIndex + 1}"
                val explanation = aiRepository.explainMistake(
                    subject = subject,
                    question = record.questionText,
                    studentAnswer = selectedText,
                    correctAnswer = correctText,
                    givenExplanation = record.explanation
                )
                _mistakeExplanation.value = AiTaskState.Success(explanation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mistakeExplanation.value = AiTaskState.Error(e.localizedMessage ?: "Could not explain mistake")
            }
        }
    }

    fun clearAssignmentFeedback() {
        _assignmentFeedback.value = AiTaskState.Idle
    }

    fun clearMistakeExplanation() {
        _mistakeExplanation.value = AiTaskState.Idle
    }
}
