package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UnlockedCollege
import com.example.data.repository.AiTutorRepository
import com.example.data.repository.CollegeRepository
import com.example.data.repository.DiscussionRepository
import com.example.data.repository.FlashcardRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.StudyMaterialRepository
import com.example.ui.screens.AiTutorScreen
import com.example.ui.screens.AnalyticsDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CollegeHubScreen
import com.example.ui.screens.DiscussionScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.UniVaultTheme
import com.example.ui.viewmodel.AiTutorViewModel
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.CollegeViewModel
import com.example.ui.viewmodel.DiscussionViewModel
import com.example.ui.viewmodel.FlashcardViewModel
import com.example.ui.viewmodel.QuizViewModel
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.ui.viewmodel.StudyMaterialViewModel
import com.example.util.WorkspaceAnalysisSnapshot
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // MANDATORY: Configure App Check before any UI or Firebase operations!
        configureAppCheck(this, intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            UniVaultTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val authState by authViewModel.uiState.collectAsState()

                    when (val state = authState) {
                        is AuthUiState.Authenticated -> {
                            val uid = state.user.uid
                            key(uid) {
                                MainAppContent(
                                    currentUserId = uid,
                                    userName = state.user.displayName ?: "Student",
                                    isDarkMode = isDarkMode,
                                    onToggleDarkMode = { isDarkMode = !isDarkMode },
                                    onSignOut = { authViewModel.signOut(this@MainActivity) }
                                )
                            }
                        }
                        is AuthUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            AuthScreen(
                                authViewModel = authViewModel,
                                authState = state
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector, val tag: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    currentUserId: String,
    userName: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val collegeRepo = remember { CollegeRepository(context) }
    val collegeViewModel = remember { CollegeViewModel(collegeRepo) }
    val selectedCollege by collegeViewModel.selectedCollege.collectAsState()

    val activeCollege = selectedCollege
    if (activeCollege == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "UniVault",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "College Access",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode (Late Night Eye Relief)",
                                tint = if (isDarkMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CollegeHubScreen(
                    viewModel = collegeViewModel,
                    currentUserId = currentUserId,
                    userName = userName
                )
            }
        }
    } else {
        key(activeCollege.collegeId) {
            CollegeWorkspaceContent(
                college = activeCollege,
                currentUserId = currentUserId,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                onBackToColleges = { collegeViewModel.exitCollege() },
                onSignOut = onSignOut
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegeWorkspaceContent(
    college: UnlockedCollege,
    currentUserId: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBackToColleges: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var selectedScreenIndex by remember { mutableIntStateOf(0) }

    BackHandler {
        onBackToColleges()
    }

    // Repositories strictly scoped to the selected college card
    val materialRepo = remember(college.collegeId) { StudyMaterialRepository(context, college.collegeId) }
    val scheduleRepo = remember(college.collegeId) { ScheduleRepository(context, college.collegeId) }
    val quizRepo = remember(college.collegeId) { QuizRepository(context, college.collegeId) }
    val flashcardRepo = remember(college.collegeId) { FlashcardRepository(context, college.collegeId) }
    val discussionRepo = remember(college.collegeId) { DiscussionRepository(context, college.collegeId) }
    val aiRepo = remember(college.collegeId) { AiTutorRepository() }

    // ViewModels
    val materialViewModel = remember(college.collegeId) { StudyMaterialViewModel(materialRepo) }
    val scheduleViewModel = remember(college.collegeId) {
        ScheduleViewModel(
            repository = scheduleRepo,
            aiRepository = aiRepo,
            appContext = context.applicationContext,
            collegeId = college.collegeId,
            collegeName = college.collegeName
        )
    }
    val quizViewModel = remember(college.collegeId) {
        QuizViewModel(
            quizRepository = quizRepo,
            aiTutorRepository = aiRepo,
            studyMaterialRepository = materialRepo,
            appContext = context.applicationContext
        )
    }
    val flashcardViewModel = remember(college.collegeId) { FlashcardViewModel(flashcardRepo, aiRepo) }
    val discussionViewModel = remember(college.collegeId) { DiscussionViewModel(discussionRepo) }
    val aiTutorViewModel = remember(college.collegeId) { AiTutorViewModel(aiRepo) }

    val allMaterials by materialViewModel.allMaterials.collectAsState()
    val allSchedules by scheduleViewModel.schedules.collectAsState()
    val scheduleMetrics by scheduleViewModel.metrics.collectAsState()
    val allQuizzes by quizViewModel.quizzes.collectAsState()
    val allTestResults by quizViewModel.testResults.collectAsState()
    val perfMetrics by quizViewModel.performanceMetrics.collectAsState()
    val allFlashcardDecks by flashcardViewModel.decks.collectAsState()
    val recentDiscussions by discussionViewModel.recentCollegeMessages.collectAsState()

    LaunchedEffect(
        college.collegeName,
        allMaterials,
        allSchedules,
        scheduleMetrics,
        allQuizzes,
        allTestResults,
        perfMetrics,
        allFlashcardDecks,
        recentDiscussions
    ) {
        aiTutorViewModel.updateWorkspaceSnapshot(
            WorkspaceAnalysisSnapshot(
                collegeName = college.collegeName,
                materials = allMaterials,
                schedules = allSchedules,
                scheduleMetrics = scheduleMetrics,
                quizzes = allQuizzes,
                testResults = allTestResults,
                performanceMetrics = perfMetrics,
                flashcardDecks = allFlashcardDecks,
                discussions = recentDiscussions
            )
        )
    }

    val navItems = listOf(
        NavItem("Vault", Icons.Default.MenuBook, "nav_vault"),
        NavItem("Calendar", Icons.Default.CalendarMonth, "nav_calendar"),
        NavItem("Quizzes", Icons.Default.Quiz, "nav_quizzes"),
        NavItem("AI Tutor", Icons.Default.AutoAwesome, "nav_ai_tutor"),
        NavItem("Discuss", Icons.Default.Chat, "nav_discuss"),
        NavItem("Progress", Icons.Default.Assessment, "nav_progress")
    )

    var isSearchExpanded by remember { mutableStateOf(false) }
    val vaultFilterState by materialViewModel.filterState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            onBackToColleges()
                        },
                        modifier = Modifier.testTag("back_to_colleges_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to College Cards"
                        )
                    }
                },
                title = {
                    if (isSearchExpanded) {
                        androidx.compose.material3.OutlinedTextField(
                            value = vaultFilterState.searchQuery,
                            onValueChange = { materialViewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp)
                                .testTag("top_search_input"),
                            placeholder = { Text("Search in ${college.collegeName}...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            trailingIcon = {
                                if (vaultFilterState.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { materialViewModel.setSearchQuery("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                    } else {
                        Column {
                            Text(
                                text = college.collegeName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "UniVault • ${navItems[selectedScreenIndex].label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedScreenIndex != 0) {
                                selectedScreenIndex = 0
                            }
                            isSearchExpanded = !isSearchExpanded
                        },
                        modifier = Modifier.testTag("top_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchExpanded) "Close search" else "Search study materials",
                            tint = if (vaultFilterState.searchQuery.isNotBlank() || isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.testTag("dark_mode_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode (Late Night Eye Relief)",
                            tint = if (isDarkMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedScreenIndex == index,
                        onClick = { selectedScreenIndex = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag(item.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedScreenIndex) {
                0 -> VaultScreen(
                    viewModel = materialViewModel,
                    currentUserId = currentUserId,
                    flashcardViewModel = flashcardViewModel
                )
                1 -> ScheduleScreen(
                    viewModel = scheduleViewModel
                )
                2 -> QuizScreen(
                    quizViewModel = quizViewModel,
                    aiTutorViewModel = aiTutorViewModel,
                    flashcardViewModel = flashcardViewModel,
                    availableNotes = allMaterials,
                    schedules = allSchedules
                )
                3 -> AiTutorScreen(
                    viewModel = aiTutorViewModel
                )
                4 -> DiscussionScreen(
                    viewModel = discussionViewModel,
                    currentUserId = currentUserId
                )
                5 -> AnalyticsDashboardScreen(
                    scheduleViewModel = scheduleViewModel,
                    quizViewModel = quizViewModel,
                    materialViewModel = materialViewModel
                )
            }
        }
    }
}
