package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.AiTutorViewModel
import com.example.ui.viewmodel.ChatMessage
import com.example.util.WorkspaceAnalysisSnapshot
import com.example.util.WorkspaceFocusMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTutorScreen(
    viewModel: AiTutorViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chat Tutor, 1: Workspace Insights, 2: Assignment Grader
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val assignmentFeedback by viewModel.assignmentFeedback.collectAsState()
    val workspaceSnapshot by viewModel.workspaceSnapshot.collectAsState()
    val useWorkspaceContext by viewModel.useWorkspaceContext.collectAsState()
    val workspaceAnalysis by viewModel.workspaceAnalysis.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("ai_tab_chat"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Tutor Chat",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("ai_tab_workspace"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Workspace Insights",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                modifier = Modifier.testTag("ai_tab_grader"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assignment Grader",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }

        when (selectedTab) {
            0 -> ChatTutorTab(
                messages = messages,
                isStreaming = isStreaming,
                workspaceSnapshot = workspaceSnapshot,
                useWorkspaceContext = useWorkspaceContext,
                onToggleWorkspaceContext = { viewModel.setUseWorkspaceContext(it) },
                onSendMessage = { viewModel.sendMessage(it) },
                onClearChat = { viewModel.clearChat() },
                onOpenWorkspaceInsights = { selectedTab = 1 }
            )
            1 -> WorkspaceInsightsTab(
                snapshot = workspaceSnapshot,
                state = workspaceAnalysis,
                onAnalyze = { mode, incVault, incCal, incProg, incDisc, incQuiz, customGoal ->
                    viewModel.analyzeWorkspace(
                        focusMode = mode,
                        includeVault = incVault,
                        includeCalendar = incCal,
                        includeProgress = incProg,
                        includeDiscuss = incDisc,
                        includeQuizzes = incQuiz,
                        customGoal = customGoal
                    )
                },
                onClear = { viewModel.clearWorkspaceAnalysis() },
                onFollowUpInChat = { prompt ->
                    selectedTab = 0
                    viewModel.sendMessage(prompt)
                }
            )
            else -> AssignmentGraderTab(
                state = assignmentFeedback,
                onSubmit = { title, course, text ->
                    viewModel.submitAssignmentForReview(title, course, text)
                },
                onClear = { viewModel.clearAssignmentFeedback() }
            )
        }
    }
}

@Composable
fun ChatTutorTab(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    workspaceSnapshot: WorkspaceAnalysisSnapshot,
    useWorkspaceContext: Boolean,
    onToggleWorkspaceContext: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onOpenWorkspaceInsights: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Analyze all my Vault notes, Calendar, Quizzes, Progress & Discussions and give me a priority study plan",
        "Based on my upcoming exams and quiz weak topics, what should I revise first?",
        "Summarize key topics & doubts across my Discuss channels and Vault files",
        "Recommend flashcards or a practice quiz topic from my stored notes"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Live Workspace Context Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (useWorkspaceContext) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = if (useWorkspaceContext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (useWorkspaceContext) "Full Workspace Context Connected" else "Workspace Context Paused",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${workspaceSnapshot.materials.size} Vault • ${workspaceSnapshot.schedules.size} Calendar • ${workspaceSnapshot.quizzes.size} Quizzes • ${workspaceSnapshot.discussions.size} Discuss • ${workspaceSnapshot.performanceMetrics.averageScorePct.toInt()}% Avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onOpenWorkspaceInsights,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Full Audit", fontSize = 11.sp)
                }
                Switch(
                    checked = useWorkspaceContext,
                    onCheckedChange = onToggleWorkspaceContext,
                    modifier = Modifier.testTag("ai_workspace_context_switch")
                )
            }
        }

        // Quick Prompt Chips + Clear Chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (messages.size > 1 && !isStreaming) {
                FilterChip(
                    selected = false,
                    onClick = onClearChat,
                    leadingIcon = {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text("New Chat", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("ai_clear_chat_chip")
                )
            }
            quickPrompts.forEach { prompt ->
                FilterChip(
                    selected = false,
                    enabled = !isStreaming,
                    onClick = { onSendMessage(prompt) },
                    label = { Text(prompt, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isAi = msg.sender == "ai"
                val cardBg = when {
                    msg.isError -> MaterialTheme.colorScheme.errorContainer
                    isAi -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
                val labelColor = when {
                    msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                    isAi -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                }
                val bodyColor = when {
                    msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                    isAi -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onPrimary
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                ) {
                    if (isAi) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (msg.isError) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (msg.isError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isAi) "UniVault Tutor" else "You",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = labelColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyColor,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (!isAi) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input"),
                placeholder = { Text("Ask about your notes, exams, quizzes, or discussions...") },
                maxLines = 3,
                shape = RoundedCornerShape(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val prompt = inputText.trim()
                    if (prompt.isNotBlank() && !isStreaming) {
                        onSendMessage(prompt)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("ai_chat_send_button")
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceInsightsTab(
    snapshot: WorkspaceAnalysisSnapshot,
    state: AiTaskState,
    onAnalyze: (
        mode: WorkspaceFocusMode,
        includeVault: Boolean,
        includeCalendar: Boolean,
        includeProgress: Boolean,
        includeDiscuss: Boolean,
        includeQuizzes: Boolean,
        customGoal: String
    ) -> Unit,
    onClear: () -> Unit,
    onFollowUpInChat: (String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(WorkspaceFocusMode.FULL_AUDIT) }
    var includeVault by remember { mutableStateOf(true) }
    var includeCalendar by remember { mutableStateOf(true) }
    var includeProgress by remember { mutableStateOf(true) }
    var includeDiscuss by remember { mutableStateOf(true) }
    var includeQuizzes by remember { mutableStateOf(true) }
    var customGoal by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "360° Workspace AI Analyzer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cross-analyze your Vault files & notes, Calendar schedule, Progress scores, Flashcards, Discuss threads, and Quizzes in one personalized report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Data Sources Coverage Grid (Tap to toggle any source)
        Text(
            text = "Connected Workspace Data Sources",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DataSourceToggleTile(
                title = "Vault Files",
                detail = "${snapshot.materials.size} notes/files",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                selected = includeVault,
                onClick = { includeVault = !includeVault },
                modifier = Modifier.weight(1f)
            )
            DataSourceToggleTile(
                title = "Calendar",
                detail = "${snapshot.classesCount} cls • ${snapshot.examsCount} exm",
                icon = Icons.Default.CalendarMonth,
                selected = includeCalendar,
                onClick = { includeCalendar = !includeCalendar },
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DataSourceToggleTile(
                title = "Progress",
                detail = "${snapshot.performanceMetrics.averageScorePct.toInt()}% avg • ${snapshot.flashcardDecks.size} decks",
                icon = Icons.Default.Assessment,
                selected = includeProgress,
                onClick = { includeProgress = !includeProgress },
                modifier = Modifier.weight(1f)
            )
            DataSourceToggleTile(
                title = "Discuss",
                detail = "${snapshot.discussions.size} messages",
                icon = Icons.Default.Chat,
                selected = includeDiscuss,
                onClick = { includeDiscuss = !includeDiscuss },
                modifier = Modifier.weight(1f)
            )
            DataSourceToggleTile(
                title = "Quizzes",
                detail = "${snapshot.quizzes.size} tests",
                icon = Icons.Default.Quiz,
                selected = includeQuizzes,
                onClick = { includeQuizzes = !includeQuizzes },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Analysis Focus Mode",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkspaceFocusMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.label, fontSize = 11.5.sp) }
                )
            }
        }

        OutlinedTextField(
            value = customGoal,
            onValueChange = { customGoal = it },
            label = { Text("Custom Question / Goal (Optional)") },
            placeholder = { Text("e.g. Build a 5-day revision plan before my CS301 exam") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workspace_analysis_goal_input"),
            maxLines = 2
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onAnalyze(
                        selectedMode,
                        includeVault,
                        includeCalendar,
                        includeProgress,
                        includeDiscuss,
                        includeQuizzes,
                        customGoal
                    )
                },
                enabled = state !is AiTaskState.Loading &&
                    (includeVault || includeCalendar || includeProgress || includeDiscuss || includeQuizzes),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("analyze_all_workspace_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state is AiTaskState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing Workspace...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze All My Data")
                }
            }

            if (state !is AiTaskState.Idle && state !is AiTaskState.Loading) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear")
                }
            }
        }

        when (state) {
            is AiTaskState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_analysis_report"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedMode.label} Report",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClear) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss report")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                onFollowUpInChat("Let's dive deeper into my ${selectedMode.label} insights—what is the #1 topic I should drill right now?")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discuss This Report in AI Tutor Chat")
                        }
                    }
                }
            }
            is AiTaskState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onClear) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DataSourceToggleTile(
    title: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AssignmentGraderTab(
    state: AiTaskState,
    onSubmit: (title: String, course: String, text: String) -> Unit,
    onClear: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Instant Assignment Evaluation & Feedback", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Paste your essay draft, lab report, or problem solution. AI Tutor will grade it against academic standards, pinpoint weaknesses, and provide detailed explanations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Assignment Title / Problem Statement") },
            placeholder = { Text("e.g. Distributed Consensus in Cloud Systems") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = course,
            onValueChange = { course = it },
            label = { Text("Course Name / Code") },
            placeholder = { Text("CS401 - Cloud Architecture") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Your Submission Content / Solution Draft") },
            placeholder = { Text("Paste your assignment solution, proofs, code logic, or text...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSubmit(title, course, content) },
                enabled = content.isNotBlank() && state !is AiTaskState.Loading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_grader_submit_button")
            ) {
                if (state is AiTaskState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grading and generating feedback...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit for Instant AI Review")
                }
            }
            if (state !is AiTaskState.Idle && state !is AiTaskState.Loading) {
                OutlinedButton(
                    onClick = {
                        title = ""
                        course = ""
                        content = ""
                        onClear()
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Clear")
                }
            }
        }

        // Feedback Result Display
        if (state is AiTaskState.Success) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Professor Evaluation & Rubric Feedback",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss feedback")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                }
            }
        } else if (state is AiTaskState.Error) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onClear) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
